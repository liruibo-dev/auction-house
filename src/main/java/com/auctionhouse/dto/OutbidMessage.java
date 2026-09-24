package com.auctionhouse.dto;

import java.math.BigDecimal;

// 只发给"刚被顶掉的那一个人"，别人不需要知道
public class OutbidMessage {

    private final String type = "OUTBID";
    private final String message = "你已被超越";
    private BigDecimal newPrice;

    public OutbidMessage(BigDecimal newPrice) {
        this.newPrice = newPrice;
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public BigDecimal getNewPrice() {
        return newPrice;
    }
}
