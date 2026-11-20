package com.zc.mybackdianping.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.zc.mybackdianping.constants.RedisConstants;
import com.zc.mybackdianping.dto.RedisData;
import com.zc.mybackdianping.entity.Shop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
@Slf4j
public class CacheClient {
    private static final Long CACHE_NULL_TTL = RedisConstants.REDIS_SHOP_NULL_EXPIRE;
    private static final Long CACHE_LOCK_TTL = RedisConstants.REDIS_SHOP_LOCK_EXPIRE;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private final StringRedisTemplate stringRedisTemplate;

    // 设置缓存
    public void setCache(String key, Object value, Long time, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    // 设置逻辑过期缓存
    public void setLogicalCache(String key, Object value, Long time){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusMinutes(time));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // 缓存穿透
    public <R,ID> R queryWithPass(
            ID id,
            String keyPrefix,
            Class<R> type,
            Function<ID, R> dbFallback,
            Long time,
            TimeUnit timeUnit
    ) {
        // 先查询缓存
        String key = keyPrefix + id;
        String shopInfo = stringRedisTemplate.opsForValue().get(key);

        // 命中缓存直接返回（shopInfo 不为 null 就是命中，其中 "" 是空值缓存）
        if (shopInfo != null) {
            return parseCacheShop(shopInfo, type);
        }

        // 不存在查询数据库
        R r = dbFallback.apply(id);

        if (r != null){
            // 设置缓存
            this.setCache(key, r, time, timeUnit);
            return r;
        }
        // 解决缓存击穿 不存在返回空值 2分钟过期
        this.setCache(key, "", CACHE_NULL_TTL, timeUnit);
        log.info("缓存不存在");
        return null;
    }

    // 缓存击穿( 互斥锁 )
    private <R,ID> R queryWithMutex(
            ID id,
            String keyPrefix,
            String lockPrefix,
            Class<R> type,
            Function<ID, R> dbFallback,
            Long time,
            TimeUnit timeUnit
    ) {
        String key = keyPrefix + id;
        // 1.先判断缓存是否命中
        String shopInfo = stringRedisTemplate.opsForValue().get(key);
        if (shopInfo != null) {
            return parseCacheShop(shopInfo, type);
        }
        // shopInfo 为 null，缓存中没有，需要去查数据库
        String lockKey = lockPrefix + id;
        boolean locked = false;
        try {
            // 2.获取不到锁就休眠重试，拿到锁的线程负责重建缓存
            while (!tryLock(lockKey)) {
                Thread.sleep(50);
            }
            locked = true;
            log.info("获取锁成功");
            // 3.再次判断缓存是否命中（等锁期间别的线程可能已经把缓存建好了）
            shopInfo = stringRedisTemplate.opsForValue().get(key);
            if (shopInfo != null) {
                return parseCacheShop(shopInfo, type);
            }
            // 4.缓存确实没有，查询数据库
            R r = dbFallback.apply(id);
            if (r == null) {
                // 解决缓存穿透 不存在缓存空值 2分钟过期
                this.setCache(key, "", CACHE_NULL_TTL, timeUnit);
                return null;
            }
            this.setCache(key, r, time, timeUnit);
            return r;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("等待获取店铺缓存锁被中断", e);
            return null;
        } finally {
            // 5.只有自己获取到的锁才释放，避免误删其他线程的锁
            if (locked) {
                unlock(lockKey);
            }
        }
    }

    // 缓存击穿( 逻辑过期 )
    private <R,ID> R queryWithLogicalExpire(
            ID id,
            String keyPrefix,
            String lockPrefix,
            Class<R> type,
            Function<ID, R> dbFallback,
            Long time

    ) {
        String key = keyPrefix + id;
        // 1.从 Redis 中查询缓存
        String shopInfo = stringRedisTemplate.opsForValue().get(key);
        // 2.缓存不存在，直接查库（逻辑过期方案下缓存一般永不过期，这里属于兜底处理）
        if (StrUtil.isBlank(shopInfo)) {
            return dbFallback.apply(id);
        }
        // 3.缓存存在
        RedisData redisData = JSONUtil.toBean(shopInfo, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 转换成shop
        Object data = redisData.getData();
        R r = JSONUtil.toBean(data.toString(), type);
        // 未过期直接返回
        if (expireTime.isAfter(LocalDateTime.now())){
            log.info("命中缓存");
            return r;
        }
        // 已过期尝试重建
        String lockKey = lockPrefix + id;
        // 获取锁
        boolean isLocked = tryLock(lockKey);
        //  - 成功 -> 开启新线程重建
        if (isLocked) {
            // todo 再次判断是否存在缓存
            // 开启新线程
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                // 重建缓存
                try {
                   R rr = dbFallback.apply(id);
                    if (rr == null) {
                        log.warn("店铺不存在，取消重建缓存，id：{}", id);
                        return;
                    }
                    this.setLogicalCache(key, rr, time);
                    log.info("缓存重构成功");
                }catch (Exception e){
                    log.error("重构失败", e);
                }finally {
                    // 这里必须释放锁 lockKey，不能误删缓存 key
                    unlock(lockKey);
                }
            });

        }
        // - 失败 -> 返回旧数据
        return r;
    }




    // 判断缓存是否存在
    private <R> R parseCacheShop(String shopInfo, Class<R> type)  {
        if (StrUtil.isBlank(shopInfo)) {
            log.info("命中空值缓存，店铺不存在");
            return null;
        }
        log.info("缓存命中");
        return JSONUtil.toBean(shopInfo, type);
    }

    /**
     * setnx 实现锁，返回值为true表示锁获取成功
     *
     * @param lockKey 锁的 key，不是缓存 key
     */
    public boolean tryLock(String lockKey){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "", CACHE_LOCK_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     *
     * @param lockKey 锁的 key，不是缓存 key
     */
    public void unlock(String lockKey){
        stringRedisTemplate.delete(lockKey);
    }

}
