package com.zc.mybackdianping.service.Impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.entity.SeckillVoucher;
import com.zc.mybackdianping.entity.VoucherOrder;
import com.zc.mybackdianping.mapper.VoucherOrderMapper;
import com.zc.mybackdianping.service.ISeckillVoucherService;
import com.zc.mybackdianping.service.IVoucherOrderService;
import com.zc.mybackdianping.utils.UserContent;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private final ISeckillVoucherService seckillVoucherService;

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
        Long id = UserContent.getUser().getId();

        // 添加线程锁
        synchronized (id.toString().intern()) {
            // 使用动态代理对象（事务）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
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

        VoucherOrder voucherOrder = getById(voucherId);
        if (Objects.equals(voucherOrder.getUserId(), UserContent.getUser().getId())){
            return Result.fail("每个用户只能购买一次");
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
