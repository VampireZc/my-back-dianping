package com.zc.mybackdianping.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zc.mybackdianping.constants.RedisConstants;
import com.zc.mybackdianping.constants.SystemConstants;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.dto.ScrollResult;
import com.zc.mybackdianping.dto.UserDTO;
import com.zc.mybackdianping.entity.Blog;
import com.zc.mybackdianping.entity.Follow;
import com.zc.mybackdianping.entity.User;
import com.zc.mybackdianping.mapper.BlogMapper;
import com.zc.mybackdianping.service.IBlogService;
import com.zc.mybackdianping.service.IFollowService;
import com.zc.mybackdianping.service.IUserService;
import com.zc.mybackdianping.utils.UserContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    private final IUserService userService;
    private final IFollowService followService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Result saveBlog(Blog blog) {
        Long userId = UserContent.getUser().getId();
        blog.setUserId(userId);
        boolean suc = save(blog);
        if (!suc){
            return Result.fail("发布失败");
        }
        // 查询当前用户所有的粉丝
        List<Follow> fans = followService.query().eq("follow_user_id", userId).list();
        if (fans.isEmpty()){
            return Result.ok();
        }
        fans.forEach(fan -> {
            String key = RedisConstants.BLOG_FANS_KEY + fan.getUserId();
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        });
        return Result.ok(blog.getId());
    }

    @Override
    public Result getHotBlogs(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        List<Blog> blogList = page.getRecords();

        blogList.forEach(blog -> {
           this.queryBlogUser(blog);
           this.isBlogLiked(blog);
        });

        return Result.ok(blogList);
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserContent.getUser().getId();
        String key = RedisConstants.BLOG_USER_LIKED + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null){ // 未点赞可以点赞
            boolean suc = update().setSql("liked = liked + 1").eq("id", id).update();
            if (suc){
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
                return Result.ok();
            }
        }else {// 已点赞取消点赞
            boolean suc = update().setSql("liked = liked - 1").eq("id", id).update();
            if (suc){
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
                return Result.ok();
            }
        }

        return Result.fail("点赞失败");

    }

    @Override
    public Result getBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null){
            return Result.fail("博客不存在");
        }
        queryBlogUser(blog); // 组装用户信息
        isBlogLiked(blog); // 组装点赞信息
        return Result.ok(blog);
    }

    @Override
    public Result queryBlogLikesTop5(Long id) {
        String key = RedisConstants.BLOG_USER_LIKED + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()){
            return Result.ok(List.of());
        }
        log.info("点赞top5: {}", top5);
        // 获取用户id列表
        List<Long> list = top5.stream()
                .map(Long::valueOf)
                .toList();

        // 指定查询顺序
        String idStr = StrUtil.join(",", list);
        // 查询用户
        List<User> userList = userService.query()
                .in("id", list)
                .last("order by field(id, " + idStr + ")")
                .list();

        List<UserDTO> dtoList = userList.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .toList();


        return Result.ok(dtoList);
    }

    @Override
    public Result getBlogsByUser(Long id, Integer current) {
        Page<Blog> page = query()
                .eq("user_id", id)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));

        List<Blog> list = page.getRecords();
        return Result.ok(list);
    }

    @Override
    public Result getBlogsByMe() {
        return getBlogsByUser(UserContent.getUser().getId(), 1);
    }

    @Override
    public Result getBlogsByFollow(Long max, Integer offset) {
        // 拿到当前用户的收件箱
        Long userId = UserContent.getUser().getId();
        String key = RedisConstants.BLOG_FANS_KEY + userId;
        // 根据收件箱解析出博客ID与时间戳
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(
                key,
                0,
                max,
                offset,
                2
        );

        if (typedTuples == null || typedTuples.isEmpty()){
            return Result.ok(List.of());
        }

        // 根据ids查询博客列表
        List<Long> ids = typedTuples.stream()
                .map(typedTuple -> Long.valueOf(typedTuple.getValue()))
                .toList();
        // 封装成排序信息
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("order by field(id, " + idStr + ")").list();
        for (Blog blog : blogs){
            // 组装用户信息
            queryBlogUser(blog);
            // 组装点赞信息
            isBlogLiked(blog);
        }

        // 计算最小时间戳和偏移量
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            // 获取分数(时间戳）
            long time = tuple.getScore().longValue();
            if(time == minTime){
                os++;
            }else{
                minTime = time;
                os = 1;
            }
        }
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);

        return Result.ok(r);
    }

    private void isBlogLiked(Blog blog) {
        Long userId = UserContent.getUser().getId();
        if (userId == null){
            // 用户未登录不展示点赞
            return;
        }
        String key = RedisConstants.BLOG_USER_LIKED + blog.getId();
        // 查询用户是否点赞
        Double isLike = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(isLike != null);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
