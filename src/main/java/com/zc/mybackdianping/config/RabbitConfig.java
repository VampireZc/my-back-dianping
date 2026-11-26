package com.zc.mybackdianping.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String SECKILL_EXCHANGE = "seckill-exchange";
    public static final String SECKILL_QUEUE = "seckill-queue";
    public static final String SECKILL_ROUTING_KEY = "seckill-order";

    // 没有消费者的等待15分钟进入死信队列
    public static final String ORDER_DELAY_EXCHANGE  = "order-delay-exchange";
    public static final String ORDER_DELAY_QUEUE     = "order-delay-queue";
    public static final String ORDER_DELAY_KEY       = "order.delay";
    // 死信队列
    public static final String ORDER_DLX_EXCHANGE    = "order-dlx-exchange";
    public static final String ORDER_CANCEL_QUEUE    = "order-cancel-queue";
    public static final String ORDER_CANCEL_KEY      = "order.cancel";

    @Value("${order.timeout.minutes:15}")
    private Integer orderTimeoutMinutes;


    @Bean
    public SimpleRabbitListenerContainerFactory orderListenerContainerFactory(ConnectionFactory cf) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(cf);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setMessageConverter(jacksonMessageConverter());
        return factory;
    }

    @Bean
    public DirectExchange orderDelayExchange(){ // 声明正常的交换机
        return new DirectExchange(ORDER_DELAY_EXCHANGE, true, false);
    }


    @Bean
    public Binding orderDelayBinding(){ // 延时队列绑定正常交换机
        return BindingBuilder.bind(orderDelayQueue()).to(orderDelayExchange()).with(ORDER_DELAY_KEY);
    }

    @Bean
    public Queue orderDelayQueue(){
        return QueueBuilder.durable(ORDER_DELAY_QUEUE) // 配置一个延时队列
                .withArgument("x-message-ttl", orderTimeoutMinutes * 60 * 1000) //消息过期时间
                .withArgument("x-dead-letter-exchange", ORDER_DLX_EXCHANGE) // 过期后进入死信交换机
                .withArgument("x-dead-letter-routing-key", ORDER_CANCEL_KEY) // 唯一key
                .build();
    }

    @Bean
    public DirectExchange orderDlxExchange(){ // 声明死信交换机
        return new DirectExchange(ORDER_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Binding orderCancelBinding(){ // 取消队列绑定死信交换机
        return BindingBuilder.bind(orderDlxQueue()).to(orderDlxExchange()).with(ORDER_CANCEL_KEY);
    }

    @Bean
    public Queue orderDlxQueue(){// 配置一个取消队列
        return QueueBuilder.durable(ORDER_CANCEL_QUEUE).build();
    }






    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
