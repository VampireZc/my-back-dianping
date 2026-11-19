package com.zc.mybackdianping.controller;

import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.entity.Shop;
import com.zc.mybackdianping.service.IShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ShopController {
    private final IShopService shopService;

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

}
