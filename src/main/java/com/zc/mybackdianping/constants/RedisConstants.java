package com.zc.mybackdianping.constants;

public class RedisConstants {
    public static final String REDIS_KEY_CODE = "login:phone:";
    public static final Long REDIS_KEY_CODE_EXPIRE = 20L;
    public static final String REDIS_USER_TOKEN = "user:token:";
    public static final Long REDIS_USER_TOKEN_EXPIRE = 30L;
    public static final String REDIS_SHOP_INFO = "shop:info:";
    public static final Long REDIS_SHOP_INFO_EXPIRE = 30L;
    public static final String REDIS_SHOP_TYPE = "shop:type";
    public static final String REDIS_SHOP_LOCK = "shop:lock:";
    public static final Long REDIS_SHOP_LOCK_EXPIRE = 10L;

    public static final Long REDIS_SHOP_NULL_EXPIRE = 2L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";
}
