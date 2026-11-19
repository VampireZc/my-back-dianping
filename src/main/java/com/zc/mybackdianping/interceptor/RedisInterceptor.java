package com.zc.mybackdianping.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.zc.mybackdianping.constants.RedisConstants;
import com.zc.mybackdianping.dto.UserDTO;
import com.zc.mybackdianping.utils.UserContent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RedisInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 从redis中获取用户
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;
        }
        String key = RedisConstants.REDIS_USER_TOKEN + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        // 判断是否存在用户
        if (userMap.isEmpty()){
            return true;
        }
        // 把map转换为UserDTO
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), true);
        UserContent.setUserContent(userDTO);
        // 重置token过期时间
        stringRedisTemplate.expire(key, RedisConstants.REDIS_USER_TOKEN_EXPIRE, TimeUnit.MINUTES);
        // 有用户放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserContent.removeUserDTO();
    }
}
