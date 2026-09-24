package com.auctionhouse.interceptor;

import com.auctionhouse.constant.SessionKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        // getSession(false)：没有 session 就返回 null，不要顺手新建一个
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SessionKeys.USER_ID) != null) {
            return true;
        }
        // 没登录，打回登录页，并且拦住这次请求不让它往下走
        response.sendRedirect("/login");
        return false;
    }
}
