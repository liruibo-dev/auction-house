package com.auctionhouse.repository;

import com.auctionhouse.entity.Bid;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BidRepository extends JpaRepository<Bid, Long> {

    List<Bid> findByItemIdOrderByBidTimeDesc(Long itemId);

    List<Bid> findByBidderIdOrderByBidTimeDesc(Long bidderId);
}
