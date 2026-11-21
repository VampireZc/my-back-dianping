package com.zc.mybackdianping.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.entity.VoucherOrder;

public interface IVoucherOrderService extends IService<VoucherOrder> {
    Result seckillVoucher(Long voucherId);

    Result createVoucherOrder(Long voucherId);
}
