package com.zc.mybackdianping;

import cn.hutool.json.JSONUtil;
import com.zc.mybackdianping.constants.RedisConstants;
import com.zc.mybackdianping.dto.RedisData;
import com.zc.mybackdianping.entity.Shop;
import com.zc.mybackdianping.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;

@SpringBootTest
class MyBackDianpingApplicationTests {

    @Autowired
    private  IShopService shopService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Test
    void contextLoads() {
        RedisData data = new RedisData();
        data.setExpireTime(LocalDateTime.now().plusSeconds(5000));
        Shop shop = shopService.getById(1L);
        data.setData(shop);
        stringRedisTemplate.opsForValue().set(RedisConstants.REDIS_SHOP_INFO + 1L, JSONUtil.toJsonStr(data));
    }

}
