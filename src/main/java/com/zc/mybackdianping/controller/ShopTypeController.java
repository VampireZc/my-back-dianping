package com.zc.mybackdianping.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.zc.mybackdianping.constants.RedisConstants;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.entity.ShopType;
import com.zc.mybackdianping.service.IShopTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/shop-type")
@Slf4j
@RequiredArgsConstructor
public class ShopTypeController {

    private final IShopTypeService shopTypeService;
    private final StringRedisTemplate stringRedisTemplate;

    @GetMapping("/list")
    public Result queryTypeList() {
        // 先查询缓存里面是否存在
        String key = RedisConstants.REDIS_SHOP_TYPE;
        String shopList = stringRedisTemplate.opsForValue().get(key);

        // 存在直接返回
        if (StrUtil.isNotBlank(shopList)) {
            log.info("缓存命中");
            // 类型转换
            List<ShopType> list = JSONUtil.toList(shopList, ShopType.class);
            return Result.ok(list);
        }

        List<ShopType> list = shopTypeService
                .query().orderByAsc("sort").list();

        // 存入缓存
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(list));
        return Result.ok(list);
    }
}
