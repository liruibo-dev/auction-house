package com.auctionhouse.service;

import com.auctionhouse.entity.Bid;

import java.math.BigDecimal;
import java.util.List;

public interface BidService {

    Bid placeBid(Long itemId, Long bidderId, BigDecimal amount);

    List<Bid> getBidHistory(Long itemId);

    List<Bid> getMyBids(Long bidderId);
}
