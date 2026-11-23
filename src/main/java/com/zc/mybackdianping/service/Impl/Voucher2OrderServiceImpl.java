package com.zc.mybackdianping.service.Impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.entity.VoucherOrder;
import com.zc.mybackdianping.mapper.VoucherOrderMapper;
import com.zc.mybackdianping.service.ISeckillVoucherService;
import com.zc.mybackdianping.service.IVoucher2OrderService;
import com.zc.mybackdianping.service.IVoucherOrderService;
import com.zc.mybackdianping.utils.UserContent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// "校验"和"下单落库"解耦，收到请求先快速判断资格，然后立刻返回，真正的数据库写入交给后台线程慢慢消化。

@Slf4j
// @Service
public class Voucher2OrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucher2OrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 阻塞队列
    private final ArrayBlockingQueue<VoucherOrder> ordersTasks = new ArrayBlockingQueue<>(1024 * 1024);
    // 线程池(单线程)
    private final static ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 当前类初始化完毕执行
    @PostConstruct
    public void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
        // 朴素写法
        // new Thread(new VoucherOrderHandler()).start();
    }

    // 匿名内部类实现Runnable接口 创建订单
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 从阻塞队列中取出订单，特点：没数据就睡觉、有数据就叫醒
                    VoucherOrder order = ordersTasks.take();
                    // 处理订单（加锁 + 事务落库）
                    handleSeckillOrder(order);
                } catch (InterruptedException e) {
                    log.error("处理订单异常");
                }
            }
        }
    }

    // 提前加载好lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    // 预加载好
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("mapper/seckill.lua")); // 脚本位于 resources/mapper 下
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 功能：秒杀下单入口，先在 Redis 层做前置校验与预扣，再异步落库，实现削峰与并发安全
    // 实现：
    //   1) 用 Lua 脚本原子完成"秒杀时间/库存/一人一单"校验并预减库存、标记用户，避免无效请求打到数据库
    //   2) 校验通过后构造订单投递到内存阻塞队列，接口立即返回，真正的 DB 写入交由后台单线程异步消费
    //   3) 后台线程消费时用 Redisson 分布式锁保证同一用户串行下单，并用动态代理调用事务方法避免自调用事务失效
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserContent.getUser().getId();

        // 执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                List.of(),
                voucherId.toString(), userId.toString()
        );
        int r = result.intValue();
        // 判断结果
        if (r != 0){
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        // 有购买资格保存订单到阻塞队列
        long orderId = System.currentTimeMillis();
        VoucherOrder order = VoucherOrder.builder()
                .id(orderId)
                .userId(UserContent.getUser().getId())
                .voucherId(voucherId)
                .build();

        // 添加阻塞队列
        log.info("添加阻塞队列");
        ordersTasks.add(order);
        return Result.ok(orderId);

    }

    // 队列消费线程调用：对同一用户加分布式锁后，通过代理调用事务方法落库
    private void handleSeckillOrder(VoucherOrder order) {
        Long userId = order.getUserId();

        RLock lock = redissonClient.getLock("order" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("用户 {} 重复下单", userId);
            return;
        }

        try {
            // 事务方法未定义在接口中，故代理需转换为实现类（依赖 Spring Boot 默认的 CGLIB 代理）；
            // 通过代理调用可解决"类内自调用导致 @Transactional 失效"的问题
            Voucher2OrderServiceImpl proxy = (Voucher2OrderServiceImpl) AopContext.currentProxy();
            proxy.saveSeckillOrder(order);
        } finally {
            lock.unlock();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveSeckillOrder(VoucherOrder order) {
        // 一人一单：本方法运行在后台线程，ThreadLocal(UserContent) 为空，必须用订单自带的 userId
        Long count = query()
                .eq("user_id", order.getUserId())
                .eq("voucher_id", order.getVoucherId())
                .count();

        if (count > 0) {
            log.error("每个用户只能购买一次，用户 {} 已下单", order.getUserId());
            return;
        }

        // 扣减库存
        boolean suc = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", order.getVoucherId())
                .gt("stock", 0)// 添加乐观锁 不超卖
                .update();

        if (!suc) {
            log.error("库存不足，voucherId={}", order.getVoucherId());
            return;
        }

        save(order);
    }
}



