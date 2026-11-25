package com.zc.mybackdianping.controller;

import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/voucher-order")
@RequiredArgsConstructor
public class OrderController {
    private final IOrderService orderService;

   @GetMapping("/page")
    public Result queryPage(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "current", defaultValue = "1") int current
   ){
       return orderService.queryPage(status, current);
   }

   @GetMapping("/{id}")
    public Result getOrder(@PathVariable("id") Long id){
        return orderService.getOrder(id);
    }

    @PutMapping("/{id}/pay")
    public Result pay(@PathVariable("id") Long id){
        return orderService.payOrder(id);
    }

    @PutMapping("/{id}/use")
    public Result use(@PathVariable("id") Long id){
        return orderService.useOrder(id);
    }

    @PutMapping("/{id}/cancel")
    public Result cancel(@PathVariable("id") Long id){
        return orderService.cancelOrder(id);
    }
}
