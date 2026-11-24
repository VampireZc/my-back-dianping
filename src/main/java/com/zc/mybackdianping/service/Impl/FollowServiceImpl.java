package com.zc.mybackdianping.service.Impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.entity.Follow;
import com.zc.mybackdianping.mapper.FollowMapper;
import com.zc.mybackdianping.service.IFollowService;
import org.springframework.stereotype.Service;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Override
    public Result follow(Integer id, Boolean isFollow) {
        return null;
    }
}
