package com.zc.mybackdianping.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zc.mybackdianping.constants.RedisConstants;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.dto.UserDTO;
import com.zc.mybackdianping.entity.Follow;
import com.zc.mybackdianping.entity.User;
import com.zc.mybackdianping.mapper.FollowMapper;
import com.zc.mybackdianping.service.IFollowService;
import com.zc.mybackdianping.service.IUserService;
import com.zc.mybackdianping.utils.UserContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    private final StringRedisTemplate stringRedisTemplate;
    private final IUserService userService;

    @Override
    public Result follow(Long id, Boolean isFollow) {
        // 先判断是否已经关注
        Long userId = UserContent.getUser().getId();
        String followKey = RedisConstants.USER_FOLLOW_KEY + userId;
        //增加关注
        if (isFollow){
            stringRedisTemplate.opsForSet().add(followKey, String.valueOf(id));
            // 添加数据库中的关注信息
            Follow follow = new Follow().setUserId(userId).setFollowUserId(id);
            save(follow);
        }
        //取消关注
        else {
            stringRedisTemplate.opsForSet().remove(followKey, String.valueOf(id));
            // 删除数据库中的关注信息
            remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", id));
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long id) {
        // 先判断是否已经关注
        Long userId = UserContent.getUser().getId();
        String followKey = RedisConstants.USER_FOLLOW_KEY + userId;
        Boolean member = stringRedisTemplate.opsForSet().isMember(followKey,String.valueOf(id));
        log.info("查询缓存");
        return Result.ok(member);
    }

    @Override
    public Result commonFollowees(Long id) {
        Long userId = UserContent.getUser().getId();
        // 获取两个集合的交集
        String followKey = RedisConstants.USER_FOLLOW_KEY + userId;
        String targetKey = RedisConstants.USER_FOLLOW_KEY + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(followKey, targetKey);
        if (intersect == null || intersect.isEmpty()) {
            log.info("没有共同关注的人");
            return Result.ok(List.of());
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).toList();
        List<User> users = userService.listByIds(ids);
        List<UserDTO> dtoList = users.stream().map(s -> BeanUtil.copyProperties(s, UserDTO.class)).toList();
        return Result.ok(dtoList);
    }
}
