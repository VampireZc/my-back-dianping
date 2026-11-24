package com.zc.mybackdianping.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zc.mybackdianping.constants.RedisConstants;
import com.zc.mybackdianping.constants.SystemConstants;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.entity.Shop;
import com.zc.mybackdianping.service.IShopService;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ShopController {
    private final IShopService shopService;
    private final StringRedisTemplate stringRedisTemplate;

    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id){
        return shopService.queryById(id);
    }

    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y
    ){
        return shopService.queryShopByType(typeId, current, x, y);
    }

    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        // 写入数据库
        return shopService.updateShop(shop);
    }

    @GetMapping("of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value= "current",defaultValue = "1") Integer current
    ){
        // 缓存到redis
        String list = stringRedisTemplate.opsForValue().get(RedisConstants.SHOP_OF_NAME_LIST);
        if (StrUtil.isNotBlank(list)){
            List<Shop> shops = JSONUtil.toList(list, Shop.class);
            if (name != null && !name.isEmpty()){
                shops = shops.stream().filter(shop -> shop.getName().contains(name)).toList();
            }
            log.info("从缓存中查询");
            return Result.ok(shops);
        }

        // 查询数据库
        Page<Shop> page = shopService.query()
                .like("name", name)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        // 缓存到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.SHOP_OF_NAME_LIST, JSONUtil.toJsonStr(page.getRecords()));
        return Result.ok(page.getRecords());
    }

}
