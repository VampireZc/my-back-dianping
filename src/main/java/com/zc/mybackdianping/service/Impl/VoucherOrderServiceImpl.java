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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
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

        // 扣减库存
        boolean suc = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
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
