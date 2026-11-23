package com.zc.mybackdianping.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zc.mybackdianping.constants.RedisConstants;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.entity.SeckillVoucher;
import com.zc.mybackdianping.entity.Voucher;
import com.zc.mybackdianping.mapper.VoucherMapper;
import com.zc.mybackdianping.service.ISeckillVoucherService;
import com.zc.mybackdianping.service.IVoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {
    private final ISeckillVoucherService seckillVoucherService;
    private final StringRedisTemplate redisTemplate;
    @Override
    public Result queryVoucherOfShop(Long shopId) {
        LambdaQueryWrapper<Voucher> queryWrapper = new LambdaQueryWrapper<Voucher>()
                .eq(Voucher::getShopId, shopId)
                .eq(Voucher::getStatus, 1);

        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(queryWrapper);
        return Result.ok(vouchers);
    }

    @Override
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = SeckillVoucher.builder()
                .voucherId(voucher.getId())
                .stock(voucher.getStock())
                .createTime(voucher.getCreateTime())
                .endTime(voucher.getEndTime())
                .beginTime(voucher.getBeginTime())
                .build();

        seckillVoucherService.save(seckillVoucher);

        // 设置库存到Redis
        redisTemplate.opsForValue().set(RedisConstants.SECKILL_STOCK_KEY + voucher.getId(), voucher.getStock().toString());

    }
}
