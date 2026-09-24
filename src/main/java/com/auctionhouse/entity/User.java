package com.auctionhouse.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
//上面都是alt+enter自动生成
@Entity//表明这个类是实体类
@Table(name = "users")//数据库名字是users，但类是User所以通过table连接起来，
public class User {
    @Id//主键
    @GeneratedValue(strategy = GenerationType.IDENTITY)//主键的值由数据库自增生成，不用自己填
    private Long id;
    @Column(nullable = false, unique = true)//用户名不能为空，且全表唯一
    private String username;
    @Column(nullable = false)//密码哈希不能为空
    private String passwordHash;
    private BigDecimal balance;
    private LocalDateTime createdAt;

    //alt+insert然后按住shift全选，确定，直接把他们的getter和setter全部自动生成出来
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
