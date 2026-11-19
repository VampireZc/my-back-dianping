package com.zc.mybackdianping.service.Impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zc.mybackdianping.constants.RedisConstants;
import com.zc.mybackdianping.constants.SystemConstants;
import com.zc.mybackdianping.dto.RedisData;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.entity.Shop;
import com.zc.mybackdianping.mapper.ShopServiceMapper;
import com.zc.mybackdianping.service.IShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopServiceMapper, Shop> implements IShopService {
    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    @Override
    public Result queryById(Long id) {
        // Shop shop = queryWithPass(id);// 解决缓存穿透
        // Shop shop = queryWithMutex(id);// 解决缓存击穿
        Shop shop = queryWithLogicalExpire(id);// 逻辑过期
        if (shop != null) return Result.ok(shop);
        return  Result.fail("店铺不存在");
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 普通查询
        Page<Shop> paged = query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        return Result.ok(paged.getRecords());

        // TODO 通过坐标查询
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null){
            return Result.fail("店铺id不能为空");
        }
        // 修改数据信息
        updateById(shop);
        // 先删除缓存
        String key = RedisConstants.REDIS_SHOP_INFO + id;
        stringRedisTemplate.delete(key);
        return Result.ok();

    }


    private Shop queryWithPass(Long id) {
        // 先查询缓存
        String key = RedisConstants.REDIS_SHOP_INFO + id;
        String shopInfo = stringRedisTemplate.opsForValue().get(key);

        // 命中缓存直接返回（shopInfo 不为 null 就是命中，其中 "" 是空值缓存）
        if (shopInfo != null) {
            return parseCacheShop(shopInfo);
        }

        // 不存在查询数据库
        Shop shop = getById(id);
        if (shop != null){
            String shopJson = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(key, shopJson, RedisConstants.REDIS_SHOP_INFO_EXPIRE, TimeUnit.MINUTES);
            return shop;
        }
        // 解决缓存击穿 不存在返回空值 2分钟过期
        stringRedisTemplate.opsForValue().set(key, "", RedisConstants.REDIS_SHOP_NULL_EXPIRE, TimeUnit.MINUTES);
        log.info("缓存不存在");
        return null;
    }

    private Shop queryWithMutex(Long id) {
        String key = RedisConstants.REDIS_SHOP_INFO + id;
        // 1.先判断缓存是否命中
        String shopInfo = stringRedisTemplate.opsForValue().get(key);
        if (shopInfo != null) {
            return parseCacheShop(shopInfo);
        }
        // shopInfo 为 null，缓存中没有，需要去查数据库

        String lockKey = RedisConstants.REDIS_SHOP_LOCK + id;
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
                return parseCacheShop(shopInfo);
            }
            // 4.缓存确实没有，查询数据库
            Shop shop = getById(id);
            if (shop == null) {
                // 解决缓存穿透 不存在缓存空值 2分钟过期
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.REDIS_SHOP_NULL_EXPIRE, TimeUnit.MINUTES);
                return null;
            }
            String shopJson = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(key, shopJson, RedisConstants.REDIS_SHOP_INFO_EXPIRE, TimeUnit.MINUTES);
            return shop;
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

    private Shop queryWithLogicalExpire(Long id) {
        String key = RedisConstants.REDIS_SHOP_INFO + id;
        // 1.从 Redis 中查询缓存
        String shopInfo = stringRedisTemplate.opsForValue().get(key);
        // 2.缓存不存在，直接查库（逻辑过期方案下缓存一般永不过期，这里属于兜底处理）
        if (StrUtil.isBlank(shopInfo)) {
            return getById(id);
        }
        // 3.缓存存在
        RedisData redisData = JSONUtil.toBean(shopInfo, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 转换成shop
        Object data = redisData.getData();
        Shop shop = JSONUtil.toBean(data.toString(), Shop.class);
        // 未过期直接返回
        if (expireTime.isAfter(LocalDateTime.now())){
            log.info("命中缓存");
            return shop;
        }
        // 已过期尝试重建
        String lockKey = RedisConstants.REDIS_SHOP_LOCK + id;
        // 获取锁
        boolean isLocked = tryLock(lockKey);
        //  - 成功 -> 开启新线程重建
        if (isLocked) {
            // todo 再次判断是否存在缓存
            // 开启新线程
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                // 重建缓存
                try {
                    Shop shopData = getById(id);
                    if (shopData == null) {
                        log.warn("店铺不存在，取消重建缓存，id：{}", id);
                        return;
                    }
                    RedisData newData = new RedisData();
                    newData.setData(shopData);
                    newData.setExpireTime(LocalDateTime.now().plusMinutes(RedisConstants.REDIS_SHOP_INFO_EXPIRE));
                    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(newData));
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
        return shop;
    }

    private Shop parseCacheShop(String shopInfo) {
        if (StrUtil.isBlank(shopInfo)) {
            log.info("命中空值缓存，店铺不存在");
            return null;
        }
        log.info("缓存命中");
        return JSONUtil.toBean(shopInfo, Shop.class);
    }

    /**
     * setnx 实现锁，返回值为true表示锁获取成功
     *
     * @param lockKey 锁的 key，不是缓存 key
     */
    public boolean tryLock(String lockKey){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", RedisConstants.REDIS_SHOP_LOCK_EXPIRE, TimeUnit.SECONDS);
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
