package com.zc.mybackdianping.utils;
import cn.hutool.core.lang.UUID;
import com.zc.mybackdianping.utils.inter.RedisLock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class RedisLockImpl implements RedisLock {

    private final StringRedisTemplate stringRedisTemplate;
    private final String name;

    private final String KEY_PREFIX = "lock:";
    private final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    // 提前加载好lua脚本
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    // 预加载好
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua")); // 设置lua脚本的位置
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    public RedisLockImpl(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(Long timeout) { // 获取锁
        String LockKey = KEY_PREFIX + name;
        String id = ID_PREFIX + Thread.currentThread().getId();
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(LockKey, id, timeout, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    // 使用lua脚本释放锁，避免误删其他线程的锁
    public void unlock(){
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                List.of(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()
        );
    }

//    @Override
//    public void unlock() { // 释放锁
//        String LockKey = KEY_PREFIX + name;
//        String currentId = stringRedisTemplate.opsForValue().get(LockKey);
//        String targetId = ID_PREFIX + Thread.currentThread().getId();
//        if (currentId.equals(targetId)) { // 只能删除自己的锁
//            stringRedisTemplate.delete(LockKey);
//        }
//    }
}
