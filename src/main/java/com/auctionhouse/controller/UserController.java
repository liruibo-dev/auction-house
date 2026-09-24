package com.auctionhouse.controller;

import com.auctionhouse.constant.SessionKeys;
import com.auctionhouse.dto.ApiResponse;
import com.auctionhouse.dto.LoginRequest;
import com.auctionhouse.dto.RegisterRequest;
import com.auctionhouse.dto.UserResponse;
import com.auctionhouse.entity.User;
import com.auctionhouse.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@RequestBody RegisterRequest request) {
        User user = userService.register(request.getUsername(), request.getPassword());
        return ApiResponse.success("注册成功，欢迎 " + user.getUsername(), null);
    }

    @PostMapping("/login")
    public ApiResponse<Void> login(@RequestBody LoginRequest request, HttpSession session) {
        User user = userService.login(request.getUsername(), request.getPassword());
        session.setAttribute(SessionKeys.USER_ID, user.getId());
        return ApiResponse.success("登录成功，欢迎 " + user.getUsername(), null);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpSession session) {
        session.invalidate();
        return ApiResponse.success("已退出登录", null);
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(HttpSession session) {
        User user = currentUser(session);
        if (user == null) {
            return ApiResponse.error(401, "未登录");
        }
        return ApiResponse.success("ok", UserResponse.from(user));
    }

    private User currentUser(HttpSession session) {
        Long userId = (Long) session.getAttribute(SessionKeys.USER_ID);
        return userService.findById(userId);
    }
}
