package com.zc.mybackdianping.service.Impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zc.mybackdianping.config.RabbitConfig;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.entity.VoucherOrder;
import com.zc.mybackdianping.mapper.VoucherOrderMapper;
import com.zc.mybackdianping.service.ISeckillVoucherService;
import com.zc.mybackdianping.service.IVoucher2OrderService;
import com.zc.mybackdianping.utils.UserContent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Primary
@Service
@Slf4j
public class Voucher3OrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucher2OrderService {

    @Resource
    private  RabbitTemplate rabbitTemplate;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    // 提前加载好lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    // 预加载好
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("mapper/seckill.lua")); // 脚本位于 resources/mapper 下
        SECKILL_SCRIPT.setResultType(Long.class);
    }

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

        // 有购买资格
        long orderId = System.currentTimeMillis();
        VoucherOrder order = VoucherOrder.builder()
                .id(orderId)
                .userId(UserContent.getUser().getId())
                .voucherId(voucherId)
                .build();

        // 发送MQ消息
        rabbitTemplate.convertAndSend(
                RabbitConfig.SECKILL_EXCHANGE,
                RabbitConfig.SECKILL_ROUTING_KEY,
                order
        );
        log.info("订单消息发送完成：{}", order);

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
            Voucher3OrderServiceImpl proxy = (Voucher3OrderServiceImpl) AopContext.currentProxy();
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

    // 监听队列消息
    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(RabbitConfig.SECKILL_QUEUE),
                    exchange = @Exchange(RabbitConfig.SECKILL_EXCHANGE),
                    key = RabbitConfig.SECKILL_ROUTING_KEY
            )
    )
    public void onSeckillMessage(VoucherOrder order) {
        log.info("收到订单消息：{}", order);
        handleSeckillOrder(order);   // 下游完全复用，一行不改
    }
}
