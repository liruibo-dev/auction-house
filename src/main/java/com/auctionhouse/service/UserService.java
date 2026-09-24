package com.auctionhouse.service;

import com.auctionhouse.entity.User;

public interface UserService {

    User register(String username, String password);

    User login(String username, String password);

    User findById(Long id);
}
