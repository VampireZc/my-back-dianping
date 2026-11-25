package com.zc.mybackdianping.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zc.mybackdianping.constants.RedisConstants;
import com.zc.mybackdianping.constants.SystemConstants;
import com.zc.mybackdianping.dto.CommentDTO;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.dto.UserDTO;
import com.zc.mybackdianping.entity.Comments;
import com.zc.mybackdianping.entity.User;
import com.zc.mybackdianping.mapper.CommentMapper;
import com.zc.mybackdianping.service.ICommentService;
import com.zc.mybackdianping.service.IUserService;
import com.zc.mybackdianping.utils.CacheClient;
import com.zc.mybackdianping.utils.UserContent;
import com.zc.mybackdianping.vo.CommentPageVO;
import com.zc.mybackdianping.vo.CommentVO;
import com.zc.mybackdianping.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comments> implements ICommentService {
    private final IUserService userService;
    private final StringRedisTemplate stringRedisTemplate;
    private final CacheClient cacheClient;

    @Override
    public Result addComments(CommentDTO commentDTO) {
        long userId = isValid();
        // 构建评论
        Comments comments = BeanUtil.copyProperties(commentDTO, Comments.class);
        int answerId = commentDTO.getAnswerId();
        comments.setUserId(userId);
        comments.setStatus(SystemConstants.DEFAULT_STATUS_CODE);
        if (answerId == SystemConstants.DEFAULT_STATUS_CODE){ // 一级评论
            comments.setAnswerId(answerId);
            comments.setParentId(answerId);
        }else { // 二级评论
            Comments target = getById(answerId);
            boolean targetIsRoot = target.getAnswerId() == null || target.getAnswerId() == 0;
            comments.setParentId(targetIsRoot ? target.getId().intValue() : target.getParentId());
            comments.setAnswerId(target.getId().intValue());   // answer 就是用户点"回复"的那条

        }
        save(comments);
        clearCache(RedisConstants.BLOG_COMMENT_PAGE + comments.getBlogId() + ":" + 1);
        log.info("添加评论成功 {}", comments);
        return Result.ok();

    }

    @Override
    public Result getCommentPage(Integer blogId, Integer current) {
        long userId = isValid();
        long expireTime =(RedisConstants.ORDER_PAGE_EXPIRE_KEY + ((long) (Math.random() * 10) + 5));

        // 使用互斥锁读取缓存
        CommentPageVO pageVO = cacheClient.queryWithMutex(
                blogId + ":" + current,
                RedisConstants.BLOG_COMMENT_PAGE,
                RedisConstants.BLOG_COMMENT_PAGE_LOCK,
                CommentPageVO.class,
                this::queryCommentPage,
                expireTime,
                TimeUnit.MINUTES
        );

        if (pageVO == null) return Result.fail("评论不存在");
        // 处理是否点赞数据
        pageVO.getRecords().forEach(comment -> {
            String key = RedisConstants.BLOG_COMMENT_LIKED + comment.getId();
            Double score = stringRedisTemplate.opsForZSet().score(key, String.valueOf(userId));
            comment.setIsLike(score != null);
        });
        return Result.ok(pageVO);

    }

    private CommentPageVO queryCommentPage(String cacheId) {
        String[] parts = cacheId.split(":");
        Integer blogId = Integer.valueOf(parts[0]);
        Integer current = Integer.valueOf(parts[1]);
        Page<Comments> page = query()
                .eq("blog_id", blogId)
                // .eq("user_id", userId)
                .eq("status", SystemConstants.DEFAULT_STATUS_CODE)
                .eq("parent_id", SystemConstants.DEFAULT_STATUS_CODE)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));


        // 组装评论VO列表
        List<CommentVO> commentsList = page.getRecords()
                .stream()
                    .map(this::buildCommentVO)
                    .toList();

        // 重新构建page
        return CommentPageVO.builder()
                .records(commentsList)
                .total(page.getTotal())
                .current(page.getCurrent())
                .size(page.getSize())
                .pages((page.getTotal() + page.getSize() - 1) / page.getSize())
                .build();
    }

    @Override
    public Result likeComment(Integer id) {
        long userId = isValid();
        String key = RedisConstants.BLOG_COMMENT_LIKED + id;
        // 判断用户是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, String.valueOf(userId));
        if (score != null){
            // 用户已经点赞，取消点赞
            stringRedisTemplate.opsForZSet().remove(key, String.valueOf(userId));
            boolean suc = update()
                    .setSql("liked = liked - 1")
                    .eq("id", id)
                    .gt("liked", 0)
                    .update();
            return Result.ok(suc);
        }

        // 点赞
        boolean suc = update()
                .setSql("liked = liked + 1")
                .eq("id", id)
                .update();
        if (suc){
            stringRedisTemplate.opsForZSet().add(key, String.valueOf(userId), System.currentTimeMillis());
        }
        return Result.ok(suc);
    }

    @Override
    @Transactional
    public Result deleteComment(Integer id) {
        long userId = isValid();
        // 查找需要删除的评论
        Comments comments = query()
                .eq("id", id)
                .eq("user_id", userId)
                .eq("status", SystemConstants.DEFAULT_STATUS_CODE)
                .one();
        if (comments == null){
            return Result.fail("评论不存在");
        }
        comments.setStatus(1);
        boolean isLevelOne = comments.getParentId().equals(SystemConstants.DEFAULT_STATUS_CODE) &&
                comments.getAnswerId().equals(SystemConstants.DEFAULT_STATUS_CODE);
        boolean b = updateById(comments);
        log.info("删除评论 {}", b);
        // 查询该评论关联的二级评论
        List<Comments> replies = query()
                .eq(isLevelOne ? "answer_id" : "parent_id" , comments.getId())
                .eq("status", SystemConstants.DEFAULT_STATUS_CODE)
                .eq("user_id", userId)
                .list();
        if (replies != null && !replies.isEmpty()){
            replies.forEach(reply -> reply.setStatus(1));
            boolean suc = updateBatchById(replies);
            log.info("删除二级评论 {}", suc);
        }
        clearCache(RedisConstants.BLOG_COMMENT_PAGE + comments.getBlogId() + ":" + 1);
        return Result.ok("删除成功");
    }

    @Override
    // Service：不缓存（PRD 约定回复不进缓存）
    public Result getReplies(Integer parentId, Integer current) {
        long userId = isValid();
        Page<Comments> page = query()
                .eq("parent_id", parentId)
                .eq("status", SystemConstants.DEFAULT_STATUS_CODE)
                .orderByAsc("create_time")          // 回复按时间正序，和一级评论一致
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));

        List<CommentVO> list = page.getRecords().stream().map(this::buildCommentVO).toList();
        // isLike 补齐别漏：这里不走缓存，但一样要按当前用户算
        list.forEach(c -> c.setIsLike(
                stringRedisTemplate.opsForZSet().score(
                        RedisConstants.BLOG_COMMENT_LIKED + c.getId(), String.valueOf(userId)) != null));
        return Result.ok(CommentPageVO.builder()
                .records(list).total(page.getTotal())
                .current(page.getCurrent()).size(page.getSize())
                .pages((page.getTotal() + page.getSize() - 1) / page.getSize())
                .build());
    }

    // 构建评论VO对象
    public CommentVO buildCommentVO(Comments comments){
        User user = userService.getOne(new QueryWrapper<User>().eq("id", comments.getUserId()));
        CommentVO vo = BeanUtil.copyProperties(comments, CommentVO.class);
        vo.setNickName(user.getNickName());
        vo.setIcon(user.getIcon());
        // 统计被回复次数：一级评论按 parent_id 计全部回复（含回复的回复），二级按 answer_id 计
        boolean isRoot = comments.getParentId() == 0;
        List<Comments> replies = query().eq(isRoot ? "parent_id" : "answer_id", comments.getId()).list();
        vo.setReplyCount((long) replies.size());
        List<CommentVO> listVo = replies.stream()
                        .map(s -> {
                            if (!s.getParentId().equals(s.getAnswerId())) {
                                // 回复的回复：前缀 @ 被直接回复者（answer）的作者
                                Comments answer = getById(s.getAnswerId());
                                User answerUser = userService.getOne(
                                        new QueryWrapper<User>()
                                                .eq("id", answer.getUserId())
                                );
                                s.setContent("回复@" + answerUser.getNickName() + "：" + s.getContent());
                            }
                            return buildCommentVO(s);
                        })
                        .toList();
        vo.setReplies(listVo);
        return vo;
    }


    public long isValid(){
        UserDTO user = UserContent.getUser();
        if (user == null){
            throw new RuntimeException("用户未登录");
        }
        return user.getId();
    }

    // 清理缓存
    public void clearCache(String key){
        stringRedisTemplate.delete(key);
    }
}
