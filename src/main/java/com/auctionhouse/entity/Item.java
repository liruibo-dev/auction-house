package com.auctionhouse.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity
@Table(name="items")//表名小写，跟 users 保持一致（Linux 上的 MySQL 区分大小写，大写会炸）

public class Item {
@Id
@GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;
    private BigDecimal startPrice;//起拍价允许为空
    private BigDecimal buyNowPrice;//一口价，可为空
    private String itemName;//物品名字
    private BigDecimal nowPrice;//当前价格
    private LocalDateTime endTime;//结束时间
    @ManyToOne//表到表的调取注释
    private User seller;//卖家，从User中调取卖家名字
    @ManyToOne
    private User currentBidder;//当前最高出价者。拍卖结束后，他就是买家
    @Enumerated(EnumType.STRING)//枚举调取专属注释
    private ItemStatus status;//枚举--物品状态，从类ItemStatus中调取
    private int bidCount;//出价次数
    // 反狙击已经延时的次数。用来封顶：不封的话最后 2 分钟可以被无限次往后拖，拍卖永远结束不了
    // columnDefinition 是故意的：ddl-auto=update 给已有表加列时，没写 DEFAULT 的话老数据的这一列会是 NULL，
    // 而 Java 这边是基本类型 int，读出来直接炸。写上 DEFAULT 0 让老行有个合法值。
    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int snipeExtendCount;
    private String description;
    private LocalDateTime createdAt;//创建时间，也就是上架时间

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getStartPrice() {
        return startPrice;
    }

    public void setStartPrice(BigDecimal startPrice) {
        this.startPrice = startPrice;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getBuyNowPrice() {
        return buyNowPrice;
    }

    public void setBuyNowPrice(BigDecimal buyNowPrice) {
        this.buyNowPrice = buyNowPrice;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public BigDecimal getNowPrice() {
        return nowPrice;
    }

    public void setNowPrice(BigDecimal nowPrice) {
        this.nowPrice = nowPrice;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public User getSeller() {
        return seller;
    }

    public void setSeller(User seller) {
        this.seller = seller;
    }

    public User getCurrentBidder() {
        return currentBidder;
    }

    public void setCurrentBidder(User currentBidder) {
        this.currentBidder = currentBidder;
    }

    public int getBidCount() {
        return bidCount;
    }

    public void setBidCount(int bidCount) {
        this.bidCount = bidCount;
    }

    public int getSnipeExtendCount() {
        return snipeExtendCount;
    }

    public void setSnipeExtendCount(int snipeExtendCount) {
        this.snipeExtendCount = snipeExtendCount;
    }

    public ItemStatus getStatus() {
        return status;
    }

    public void setStatus(ItemStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }


}
