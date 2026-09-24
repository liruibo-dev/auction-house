package com.auctionhouse.component;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuctionLockRegistry {

    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<Long, Object>();

    public Object lockFor(Long itemId) {
        return locks.computeIfAbsent(itemId, key -> new Object());
    }
}
