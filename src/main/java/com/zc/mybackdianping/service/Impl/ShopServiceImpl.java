package com.zc.mybackdianping.service.Impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zc.mybackdianping.constants.RedisConstants;
import com.zc.mybackdianping.constants.SystemConstants;
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

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopServiceMapper, Shop> implements IShopService {
    /**
     * 缓存未命中的哨兵对象，用于区分「未命中」和「命中空值缓存」
     */
    private static final Shop CACHE_NOT_HIT = new Shop();

    private final StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        // Shop shop = queryWithPass(id);// 解决缓存穿透
        Shop shop = queryWithMutex(id);// 解决缓存击穿
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

        // 存在直接返回
        if (StrUtil.isNotBlank(shopInfo)){
            Shop shop = JSONUtil.toBean(shopInfo, Shop.class);
            log.info("缓存命中");
            return shop;
        }

        if (shopInfo != null) return null;

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
        Shop shop = getCacheShop(key);
        if (shop != CACHE_NOT_HIT) return shop;

        String lockKey = RedisConstants.REDIS_SHOP_LOCK + id;
        boolean locked = false;
        try {
            // 2.获取不到锁就休眠重试，拿到锁的线程负责重建缓存
            while (!tryLock(lockKey)) {
                Thread.sleep(50);
            }
            locked = true;
            log.info("获取锁成功");
            // 3.再次判断缓存是否命中
            shop = getCacheShop(key);
            if (shop != CACHE_NOT_HIT) return shop;
            // 4.不存在查询数据库
            shop = getById(id);
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

    /**
     * 读取缓存中的店铺信息
     *
     * @return 未命中返回 CACHE_NOT_HIT，命中空值缓存（""）或正常数据返回对应结果
     */
    private Shop getCacheShop(String key) {
        String shopInfo = stringRedisTemplate.opsForValue().get(key);
        if (shopInfo == null) {
            // 缓存不存在
            return CACHE_NOT_HIT;
        }
        if (StrUtil.isBlank(shopInfo)) {
            // 命中空值缓存，说明店铺不存在，无需再查数据库
            log.info("命中空值缓存");
            return null;
        }
        log.info("缓存命中");
        return JSONUtil.toBean(shopInfo, Shop.class);
    }

    /**
     * setnx 实现锁，返回值为true表示锁获取成功
     */
    public boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    
    public void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
