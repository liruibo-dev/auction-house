package com.auctionhouse.dto;

import com.auctionhouse.entity.User;

import java.math.BigDecimal;

public class UserResponse {

    private Long id;
    private String username;
    private BigDecimal balance;

    public UserResponse(Long id, String username, BigDecimal balance) {
        this.id = id;
        this.username = username;
        this.balance = balance;
    }

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getBalance());
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public BigDecimal getBalance() {
        return balance;
    }
}
