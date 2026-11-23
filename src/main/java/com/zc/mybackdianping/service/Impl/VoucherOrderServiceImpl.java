package com.zc.mybackdianping.service.Impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.entity.SeckillVoucher;
import com.zc.mybackdianping.entity.VoucherOrder;
import com.zc.mybackdianping.mapper.VoucherOrderMapper;
import com.zc.mybackdianping.service.ISeckillVoucherService;
import com.zc.mybackdianping.service.IVoucherOrderService;
import com.zc.mybackdianping.utils.RedisLockImpl;
import com.zc.mybackdianping.utils.UserContent;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private final ISeckillVoucherService seckillVoucherService;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    // 使用 Redis 分布式锁（替代 JVM synchronized）保证集群部署下"一人一单"的并发安全，
    // 并配合动态代理对象解决类内自调用导致的 @Transactional 事务失效问题，保证事务提交再释放锁
    // 使用 Lua 脚本保证"释放锁"时判断持有者与删除的原子性，避免误删其他线程的锁
    // 改用 Redisson：自带看门狗自动续期与可重入，省去手写锁与 unlock.lua
    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (ObjectUtil.isEmpty(voucher)) {
            return Result.fail("秒杀信息不存在");
        }
        // 判断秒杀是否开始
        if (!(LocalDateTime.now().isAfter(voucher.getBeginTime()) &&
                LocalDateTime.now().isBefore(voucher.getEndTime()))){
            return Result.fail("秒杀已结束");
        }

        if (voucher.getStock() <= 0){
            return Result.fail("库存不足");
        }
        Long usr_id = UserContent.getUser().getId();

        // 添加线程锁
        // RedisLockImpl redisLock = new RedisLockImpl(stringRedisTemplate, "order" + usr_id);
        // 获取锁
        // boolean isLock = redisLock.tryLock(10L);

        //synchronized (id.toString().intern()) {

        //  Redisson 一行搞定，自带看门狗自动续
        RLock lock = redissonClient.getLock("order" + usr_id);
        try {
            boolean isLock = lock.tryLock(10, TimeUnit.SECONDS);
            if (!isLock) {
                return Result.fail("请勿重复下单");
            }
            // 使用动态代理对象（事务）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }catch (Exception e){
            return Result.fail("下单失败");
        }finally {
            lock.unlock();
        }

        //}
    }

    @Transactional(rollbackFor = Exception.class)
    public Result createVoucherOrder(Long voucherId) {
        // 一人一单
        Long count = query()
                        .eq("user_id", UserContent.getUser().getId())
                        .eq("voucher_id", voucherId)
                        .count();

        if (count > 0) {
            return Result.fail("每个用户只能购买一次");
        }

        // 扣减库存
        boolean suc = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)// 添加乐观锁 不超卖
                .update();

        if (!suc) {
            return Result.fail("库存不足");
        }

        // 创建订单
        VoucherOrder order = VoucherOrder.builder()
                .id(System.currentTimeMillis())
                .userId(UserContent.getUser().getId())
                .voucherId(voucherId)
//                .payType(0)
//                .status(0)
                .build();

        save(order);
        return Result.ok(order.getId());
    }
}
