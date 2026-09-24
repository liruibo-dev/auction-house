package com.auctionhouse.dto;

import com.auctionhouse.entity.Bid;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BidResponse {

    private Long id;
    private String bidderName;
    private BigDecimal amount;
    private LocalDateTime bidTime;

    public static BidResponse from(Bid bid) {
        BidResponse response = new BidResponse();
        response.id = bid.getId();
        response.amount = bid.getAmount();
        response.bidTime = bid.getBidTime();
        if (bid.getBidder() != null) {
            response.bidderName = bid.getBidder().getUsername();
        }
        return response;
    }

    public Long getId() {
        return id;
    }

    public String getBidderName() {
        return bidderName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getBidTime() {
        return bidTime;
    }
}
