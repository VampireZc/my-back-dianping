package com.zc.mybackdianping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;


@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class MyBackDianpingApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyBackDianpingApplication.class, args);
    }

}
