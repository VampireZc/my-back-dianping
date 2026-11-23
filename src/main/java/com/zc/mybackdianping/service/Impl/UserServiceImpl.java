package com.zc.mybackdianping.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zc.mybackdianping.constants.RedisConstants;
import com.zc.mybackdianping.dto.LoginFormDTO;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.dto.UserDTO;
import com.zc.mybackdianping.entity.User;
import com.zc.mybackdianping.mapper.UserMapper;
import com.zc.mybackdianping.service.IUserService;
import com.zc.mybackdianping.utils.RegexUtils;
import com.zc.mybackdianping.constants.SystemConstants;
import com.zc.mybackdianping.utils.UserContent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号码
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号码格式无效");
        }
        // 生成验证码
        String code = RandomUtil.randomNumbers(4);
        // 把验证码存入redis
        // session.setAttribute(SystemConstants.USER_SESSION_CODE, code);
        stringRedisTemplate.opsForValue().set(RedisConstants.REDIS_KEY_CODE + phone, code, RedisConstants.REDIS_KEY_CODE_EXPIRE, TimeUnit.MINUTES);
        log.info("发送验证码：{}给{}", code, phone);
        return Result.ok(code);
    }

    @Override
    public Result login(LoginFormDTO loginFrom, HttpSession session) {
        String phone = loginFrom.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号码格式无效");
        }

        String code = loginFrom.getCode();
        // 从redis中获取验证码
        String redisCode = stringRedisTemplate.opsForValue().get(RedisConstants.REDIS_KEY_CODE + phone);
        // String sessionCode = session.getAttribute(SystemConstants.USER_SESSION_CODE).toString();
        log.info("获取的验证码：{}", redisCode);
        if (redisCode == null) {
            return Result.fail("验证码无效");
        }
        if (!code.equals(redisCode)) {
            return Result.fail("验证码无效");
        }
        User user = query().eq("phone", phone).one();
        // 用户不存在则注册
        if (user == null) {
            user = new User();
            user.setPhone(phone);
            user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
            user.setCreateTime(LocalDateTime.now());
            save(user);
        }
        // 保存用户
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        String token = UUID.randomUUID().toString(true);

        // 将用户信息以map形式保存到redis中
        // Map<String, Object> beanToMap = BeanUtil.beanToMap(userDTO);

        HashMap<Object, Object> beanToMap = new HashMap<>();
        beanToMap.put("id", userDTO.getId().toString());
        beanToMap.put("nickName", userDTO.getNickName());
        beanToMap.put("icon", "https://q7.itc.cn/q_70/images03/20250304/22ffb9a0eeb04cbf9a1783a18f6b4ccc.jpeg");

        log.info("保存用户信息到redis：{}", beanToMap);
        String key = RedisConstants.REDIS_USER_TOKEN + token;
        stringRedisTemplate.opsForHash().putAll(key, beanToMap);
        stringRedisTemplate.expire(key, RedisConstants.REDIS_USER_TOKEN_EXPIRE, TimeUnit.MINUTES);
        // session.setAttribute(SystemConstants.USER_SESSION_KEY, userDTO);
        return Result.ok(token);
    }

    @Override
    public Result logout() {
        String token = UserContent.getUser().getToken();
        if (StrUtil.isBlank(token)) {
            return Result.fail("未登录");
        }
        // 删除 redis 中该 token 对应的用户信息，使令牌立即失效
        stringRedisTemplate.delete(RedisConstants.REDIS_USER_TOKEN + token);
        // 清理当前线程的用户上下文
        UserContent.removeUserDTO();
        return Result.ok();
    }


}
