package com.zc.mybackdianping.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zc.mybackdianping.constants.RedisConstants;
import com.zc.mybackdianping.constants.SystemConstants;
import com.zc.mybackdianping.dto.OrderVO;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.entity.VoucherOrder;
import com.zc.mybackdianping.mapper.OrderMapper;
import com.zc.mybackdianping.service.IOrderService;
import com.zc.mybackdianping.service.IVoucherOrderService;
import com.zc.mybackdianping.utils.UserContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements IOrderService {
    private final OrderMapper orderMapper;
    private final IVoucherOrderService voucherOrderService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryPage(Integer status, int current) {
        Long userId = UserContent.getUser().getId();
        if (userId == null){
            return Result.fail("用户未登录");
        }
        // 设置版本号
        String verKey = RedisConstants.ORDER_PAGE_VER_KEY + userId;
        // 设置版本号初始值为0
        Long increment = stringRedisTemplate.opsForValue().increment(verKey, 0);
        String listKey = RedisConstants.ORDER_PAGE_KEY + userId + ":" + increment + ":" + status;

        List<OrderVO> orderVOList = Collections.emptyList();
        String string = stringRedisTemplate.opsForValue().get(listKey);
        if (string != null){
            log.info("从缓存中获取订单列表");
            orderVOList = JSONUtil.toList(string, OrderVO.class);
        }else { // 查数据库构建缓存
            // 随机时间延迟
            long expireTime =(RedisConstants.ORDER_PAGE_EXPIRE_KEY + ((long) (Math.random() * 10) + 5));
            log.info("重建列表缓存：{}, 过期时间：{} ", status, expireTime);
            orderVOList = orderMapper.queryByUserID(userId, status);
            stringRedisTemplate.opsForValue().set(
                    listKey,
                    JSONUtil.toJsonStr(orderVOList),
                    expireTime,
                    TimeUnit.MINUTES
            );
        }

        Page<OrderVO> page = new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE);
        page.setRecords(orderVOList);

        return Result.ok(page);
    }

    @Override
    public Result getOrder(Long id) {
        Long userId = UserContent.getUser().getId();
        if (userId == null){
            return Result.fail("用户未登录，无法获取订单");
        }
        OrderVO orderVO = orderMapper.queryByOrderID(userId,  id);
        return Result.ok(orderVO);
    }

    @Override
    public Result payOrder(Long id) {
        Long userId = UserContent.getUser().getId();

        if (isValidOrder(id, userId, "1")){
            return Result.fail("订单不存在");
        }

        boolean update = voucherOrderService.update()
                .set("status", 2)
                .set("pay_time", LocalDateTime.now())
                .set("pay_type", 1)
                .eq("id", id)
                .eq("user_id", userId)
                .update();

        if (!update){
            return Result.fail("支付失败");
        }

        log.info("订单支付成功");
        updateVersion(userId); // 修改版本号
        return Result.ok("支付成功");
    }

    @Override
    public Result useOrder(Long id) {
        Long userId = UserContent.getUser().getId();

        if (isValidOrder(id, userId, "2")){
            return Result.fail("订单不存在");
        }

        boolean update = voucherOrderService.update()
                .set("use_time", LocalDateTime.now())
                .set("status", 3)
                .eq("id", id)
                .eq("user_id", userId)
                .update();

        if (!update){
            return Result.fail("核销失败");
        }
        log.info("订单核销成功");
        updateVersion(userId); // 修改版本号
        return Result.ok("核销成功");
    }

    @Override
    public Result cancelOrder(Long id) {
        Long userId = UserContent.getUser().getId();

        if (isValidOrder(id, userId, "1")){
            return Result.fail("订单不存在");
        }


        boolean update = voucherOrderService.update()
                .set("refund_time", LocalDateTime.now())
                .set("status", 4)
                .eq("id", id)
                .eq("user_id", userId)
                .update();

        if (!update){
            return Result.fail("取消失败");
        }
        log.info("订单取消成功");
        updateVersion(userId); // 修改版本号
        return Result.ok("取消成功");
    }


    // 判断订单是否有效
    public boolean isValidOrder(Long id, Long userId, String status){
        VoucherOrder order = voucherOrderService.getOne(
                new QueryWrapper<VoucherOrder>()
                        .eq("id", id)
                        .eq("user_id", userId)
                        .eq("status", status)
        );

        return BeanUtil.isEmpty(order);
    }

    // 修改版本号
    public void updateVersion(Long userId){
        String verKey = RedisConstants.ORDER_PAGE_VER_KEY + userId;
        // 增加版本号
        stringRedisTemplate.opsForValue().increment(verKey);
    }

}
