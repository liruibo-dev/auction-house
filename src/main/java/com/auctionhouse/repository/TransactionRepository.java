package com.auctionhouse.repository;

import com.auctionhouse.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByBuyerIdOrderByCompletedAtDesc(Long buyerId);

    List<Transaction> findBySellerIdOrderByCompletedAtDesc(Long sellerId);
}
