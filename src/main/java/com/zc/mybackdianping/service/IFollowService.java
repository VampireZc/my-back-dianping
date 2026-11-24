package com.zc.mybackdianping.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.entity.Follow;

public interface IFollowService extends IService<Follow> {
    // 点击关注
    Result follow(Long id, Boolean isFollow);

    // 是否关注
    Result isFollow(Long id);

    // 共同关注
    Result commonFollowees(Long id);
}
