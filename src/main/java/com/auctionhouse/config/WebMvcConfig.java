package com.auctionhouse.config;

import com.auctionhouse.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;

    public WebMvcConfig(LoginInterceptor loginInterceptor) {
        this.loginInterceptor = loginInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                // 只点名拦这两类页面。不写成"全拦再排除"，是因为 /auctions 和 /auctions/create
                // 长得太像，排除 /auctions/* 会把 create 一起放过，规则互相打架。
                .addPathPatterns("/auctions/create", "/my/**");
    }
}
