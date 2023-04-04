package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {

//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        // 1.判断是否需要拦截（ThreadLocal中是否有用户）
//        if (UserHolder.getUser() == null) {
//            // 没有，需要拦截，设置状态码
//            response.setStatus(401);
//            // 拦截
//            return false;
//        }
//        // 有用户，则放行
//        return true;
//    }


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession();
        UserDTO user = (UserDTO) session.getAttribute("user");
        // 1.判断是否需要拦截（session中是否有用户）
        if (user == null) {
            response.setStatus(401);
            return false;
        }
        UserHolder.saveUser(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder
                .removeUser();
    }
}
