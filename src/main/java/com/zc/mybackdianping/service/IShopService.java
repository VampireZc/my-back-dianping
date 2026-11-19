package com.zc.mybackdianping.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.entity.Shop;

public interface IShopService extends IService<Shop> {
    Result queryById(Long id);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);

    Result updateShop(Shop shop);
}
