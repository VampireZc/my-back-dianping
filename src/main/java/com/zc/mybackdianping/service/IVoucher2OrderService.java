package com.zc.mybackdianping.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.entity.VoucherOrder;

public interface IVoucher2OrderService extends IService<VoucherOrder> {
    Result seckillVoucher(Long voucherId);

    void saveSeckillOrder(VoucherOrder order);
}
