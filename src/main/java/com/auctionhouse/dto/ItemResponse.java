package com.auctionhouse.dto;

import com.auctionhouse.entity.Item;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ItemResponse {

    private Long id;
    private String itemName;
    private String description;
    private BigDecimal startPrice;
    private BigDecimal nowPrice;
    private BigDecimal buyNowPrice;
    private int bidCount;
    private String status;
    private String statusName;
    private LocalDateTime endTime;
    private String sellerName;
    private String currentBidderName;

    public static ItemResponse from(Item item) {
        ItemResponse response = new ItemResponse();
        response.id = item.getId();
        response.itemName = item.getItemName();
        response.description = item.getDescription();
        response.startPrice = item.getStartPrice();
        response.nowPrice = item.getNowPrice();
        response.buyNowPrice = item.getBuyNowPrice();
        response.bidCount = item.getBidCount();
        response.endTime = item.getEndTime();
        if (item.getStatus() != null) {
            response.status = item.getStatus().name();
            response.statusName = item.getStatus().getStatusName();
        }
        if (item.getSeller() != null) {
            response.sellerName = item.getSeller().getUsername();
        }
        if (item.getCurrentBidder() != null) {
            response.currentBidderName = item.getCurrentBidder().getUsername();
        }
        return response;
    }

    public Long getId() {
        return id;
    }

    public String getItemName() {
        return itemName;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getStartPrice() {
        return startPrice;
    }

    public BigDecimal getNowPrice() {
        return nowPrice;
    }

    public BigDecimal getBuyNowPrice() {
        return buyNowPrice;
    }

    public int getBidCount() {
        return bidCount;
    }

    public String getStatus() {
        return status;
    }

    public String getStatusName() {
        return statusName;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public String getSellerName() {
        return sellerName;
    }

    public String getCurrentBidderName() {
        return currentBidderName;
    }
}
