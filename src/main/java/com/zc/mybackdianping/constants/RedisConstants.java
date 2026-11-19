package com.zc.mybackdianping.constants;

public class RedisConstants {
    public static final String REDIS_KEY_CODE = "login:phone:";
    public static final Integer REDIS_KEY_CODE_EXPIRE = 20;
    public static final String REDIS_USER_TOKEN = "user:token:";
    public static final Integer REDIS_USER_TOKEN_EXPIRE = 30;
    public static final String REDIS_SHOP_INFO = "shop:info:";
    public static final Integer REDIS_SHOP_INFO_EXPIRE = 30;
    public static final String REDIS_SHOP_TYPE = "shop:type";
    public static final String REDIS_SHOP_LOCK = "shop:lock:";

    public static final Integer REDIS_SHOP_NULL_EXPIRE = 2;
}
