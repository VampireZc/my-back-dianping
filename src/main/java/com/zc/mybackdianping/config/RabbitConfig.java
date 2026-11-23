package com.zc.mybackdianping.config;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String SECKILL_EXCHANGE = "seckill-exchange";
    public static final String SECKILL_QUEUE = "seckill-queue";
    public static final String SECKILL_ROUTING_KEY = "seckill-order";

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
