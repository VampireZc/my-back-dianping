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

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String SECKILL_ORDER_KEY = "seckill:order:";

    public static final String SHOP_OF_NAME_LIST = "shopName:list";

    public static final String BLOG_USER_LIKED = "blog:liked:";

    public static final String USER_FOLLOW_KEY = "user:follow:";

    public static final String BLOG_FANS_KEY = "blog:fans:";

    public static final String ORDER_PAGE_VER_KEY = "order:ver:";
    public static final String ORDER_PAGE_KEY = "order:page:";
    public static final Long ORDER_PAGE_EXPIRE_KEY = 10L;
}
