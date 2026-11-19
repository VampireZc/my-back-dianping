package com.zc.mybackdianping.config;

import com.zc.mybackdianping.interceptor.LoginInterceptor;
import com.zc.mybackdianping.interceptor.RedisInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 添加登录拦截器
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns( // 配置不拦截的路径
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login"
                ).order(1);

        // 添加Redis拦截器先执行
        registry.addInterceptor(new RedisInterceptor(stringRedisTemplate))
                .addPathPatterns("/**").order(0);
    }


}
