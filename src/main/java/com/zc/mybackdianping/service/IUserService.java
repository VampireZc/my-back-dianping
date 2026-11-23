package com.zc.mybackdianping.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.zc.mybackdianping.dto.LoginFormDTO;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.entity.User;
import jakarta.servlet.http.HttpSession;

public interface IUserService extends IService<User> {
    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginFrom, HttpSession session);

    Result logout();
}
