package com.zc.mybackdianping.controller;

import com.zc.mybackdianping.dto.LoginFormDTO;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.dto.UserDTO;
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
}
