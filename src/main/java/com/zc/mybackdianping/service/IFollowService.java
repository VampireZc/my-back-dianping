package com.zc.mybackdianping.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.entity.Follow;

public interface IFollowService extends IService<Follow> {
    Result follow(Integer id, Boolean isFollow);
}
