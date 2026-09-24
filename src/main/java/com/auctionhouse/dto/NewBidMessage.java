package com.auctionhouse.dto;

import java.math.BigDecimal;

// 有人出价成功后，广播给这个拍卖房间里所有围观者的消息
public class NewBidMessage {

    private final String type = "NEW_BID";
    private String bidderName;
    private BigDecimal amount;
    private String newEndTime;
    private int bidCount;

    public NewBidMessage(String bidderName, BigDecimal amount, String newEndTime, int bidCount) {
        this.bidderName = bidderName;
        this.amount = amount;
        this.newEndTime = newEndTime;
        this.bidCount = bidCount;
    }

    public String getType() {
        return type;
    }

    public String getBidderName() {
        return bidderName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getNewEndTime() {
        return newEndTime;
    }

    public int getBidCount() {
        return bidCount;
    }
}
