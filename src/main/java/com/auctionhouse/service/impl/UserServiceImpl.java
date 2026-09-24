package com.auctionhouse.service.impl;

import com.auctionhouse.entity.User;
import com.auctionhouse.exception.DuplicateUsernameException;
import com.auctionhouse.exception.InvalidLoginException;
import com.auctionhouse.exception.InvalidRegisterException;
import com.auctionhouse.repository.UserRepository;
import com.auctionhouse.service.UserService;
import com.auctionhouse.util.PasswordUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class UserServiceImpl implements UserService {

    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000.00");
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public User register(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new InvalidRegisterException("用户名不能为空");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidRegisterException("密码不能少于 " + MIN_PASSWORD_LENGTH + " 位");
        }
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateUsernameException("用户名 " + username + " 已被占用");
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(PasswordUtil.hash(password));
        user.setBalance(INITIAL_BALANCE);
        user.setCreatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    @Override
    public User login(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new InvalidLoginException("用户名和密码不能为空");
        }

        User user = userRepository.findByUsername(username);
        // 用户不存在与密码错误返回同一条提示，避免暴露哪些用户名已被注册
        if (user == null || !PasswordUtil.verify(password, user.getPasswordHash())) {
            throw new InvalidLoginException("用户名或密码错误");
        }
        return user;
    }

    @Override
    public User findById(Long id) {
        if (id == null) {
            return null;
        }
        return userRepository.findById(id).orElse(null);
    }
}
