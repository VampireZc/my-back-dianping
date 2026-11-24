package com.zc.mybackdianping.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.zc.mybackdianping.dto.LoginFormDTO;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.entity.User;
import jakarta.servlet.http.HttpSession;

public interface IUserService extends IService<User> {
    // 发送验证码
    Result sendCode(String phone, HttpSession session);

    // 登录
    Result login(LoginFormDTO loginFrom, HttpSession session);

    // 登出
    Result logout();

    // 用户主页
    Result getUserById(Long id);
}
