package com.zc.mybackdianping.utils;

import com.zc.mybackdianping.dto.UserDTO;

public class UserContent {
    private final static ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void setUserContent(UserDTO user) {
        tl.set(user);
    }

    public static UserDTO getUser() {
        return tl.get();
    }

    public static void removeUserDTO() {
        tl.remove();
    }
}
