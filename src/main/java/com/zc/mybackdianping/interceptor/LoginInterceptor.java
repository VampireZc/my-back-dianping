package com.zc.mybackdianping.interceptor;
import com.zc.mybackdianping.utils.UserContent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.判断是否需要拦截（ThreadLocal中是否有用户）
        if (UserContent.getUser() == null) {
            // 没有，需要拦截，设置状态码
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            // 拦截
            return false;
        }
        // 有用户，则放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserContent.removeUserDTO();
    }
}
