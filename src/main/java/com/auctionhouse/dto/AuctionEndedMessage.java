package com.auctionhouse.dto;

import java.math.BigDecimal;

// 拍卖结束时广播。成交就带赢家和成交价；流拍（没人出价或买家余额不够）winnerName 是 null
public class AuctionEndedMessage {

    private final String type = "AUCTION_ENDED";
    private String winnerName;
    private BigDecimal finalPrice;
    private Long buyerId;

    public static AuctionEndedMessage sold(String winnerName, BigDecimal finalPrice, Long buyerId) {
        AuctionEndedMessage message = new AuctionEndedMessage();
        message.winnerName = winnerName;
        message.finalPrice = finalPrice;
        message.buyerId = buyerId;
        return message;
    }

    public static AuctionEndedMessage unsold() {
        return new AuctionEndedMessage();
    }

    public String getType() {
        return type;
    }

    public String getWinnerName() {
        return winnerName;
    }

    public BigDecimal getFinalPrice() {
        return finalPrice;
    }

    public Long getBuyerId() {
        return buyerId;
    }
}
