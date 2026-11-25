package com.zc.mybackdianping.service;

import com.zc.mybackdianping.dto.Result;

public interface IOrderService {
    // 查询订单分页列表
    Result queryPage(Integer status, int current);

    // 查询订单详情
    Result getOrder(Long id);

    // 订单支付
    Result payOrder(Long id);

    // 核销订单
    Result useOrder(Long id);

    // 取消订单
    Result cancelOrder(Long id);
}
