package com.zc.mybackdianping.controller;

import cn.hutool.core.bean.BeanUtil;
import com.zc.mybackdianping.dto.LoginFormDTO;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.dto.UserDTO;
import com.zc.mybackdianping.entity.User;
import com.zc.mybackdianping.service.IUserService;
import com.zc.mybackdianping.utils.UserContent;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {
    private final IUserService userService;

    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String Phone, HttpSession session){
      return  userService.sendCode(Phone, session);
    }

    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginFrom, HttpSession session){
        return userService.login(loginFrom, session);
    }

    @GetMapping("/me")
    public Result me(){
        UserDTO userDTO = UserContent.getUser();
        return Result.ok(userDTO);
    }

    @PostMapping("/logout")
    public Result logout(){
        return userService.logout();
    }

    @GetMapping("/{id}")
    public Result getUserById(@PathVariable("id") Long id){
        return userService.getUserById(id);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        User info = userService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(info, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }
}
