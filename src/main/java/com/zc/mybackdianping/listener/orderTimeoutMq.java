package com.zc.mybackdianping.listener;

import com.rabbitmq.client.Channel;
import com.zc.mybackdianping.config.RabbitConfig;
import com.zc.mybackdianping.service.IOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class orderTimeoutMq {
    private final IOrderService orderService;
    @RabbitListener( // 监听订单取消队列
            queues = RabbitConfig.ORDER_CANCEL_QUEUE,
            containerFactory = "orderListenerContainerFactory"
    )
    public void onOrderTimeoutMsg(
            Long id, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    )throws IOException{
        log.info("订单超时，取消订单：{}", id);
        try {
            // 开始处理订单超时
            orderService.cancelOrder(id, true);
            // 手动ack消息
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 手动确认失败
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
