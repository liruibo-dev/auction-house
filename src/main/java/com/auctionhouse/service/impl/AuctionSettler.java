package com.auctionhouse.service.impl;

import com.auctionhouse.component.AuctionLockRegistry;
import com.auctionhouse.dto.AuctionEndedMessage;
import com.auctionhouse.entity.Item;
import com.auctionhouse.entity.ItemStatus;
import com.auctionhouse.entity.Transaction;
import com.auctionhouse.entity.User;
import com.auctionhouse.repository.ItemRepository;
import com.auctionhouse.repository.TransactionRepository;
import com.auctionhouse.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class AuctionSettler {

    private static final Logger log = LoggerFactory.getLogger(AuctionSettler.class);

    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final AuctionLockRegistry lockRegistry;

    public AuctionSettler(ItemRepository itemRepository,
                          UserRepository userRepository,
                          TransactionRepository transactionRepository,
                          AuctionLockRegistry lockRegistry) {
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.lockRegistry = lockRegistry;
    }

    /**
     * 结算一场拍卖，返回"该广播给围观者的消息"，不需要广播时返回 null。
     *
     * 这里只负责把状态改完，广播交给调用方去做，原因见 AuctionSettlementServiceImpl。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuctionEndedMessage settleOne(Long itemId) {
        // 与出价用同一把锁：出价先拿到锁，结算就得等出价做完，反之亦然
        synchronized (lockRegistry.lockFor(itemId)) {
            Item item = itemRepository.findById(itemId).orElse(null);
            if (item == null || item.getStatus() != ItemStatus.ACTIVE) {
                return null;
            }

            LocalDateTime now = LocalDateTime.now();
            if (item.getEndTime().isAfter(now)) {
                // 刚刚被防狙击延时了，还没到点
                return null;
            }

            if (item.getCurrentBidder() == null || item.getBidCount() == 0) {
                item.setStatus(ItemStatus.CANCELLED);
                itemRepository.save(item);
                log.info("拍卖 {} 无人出价，已流拍", itemId);
                return AuctionEndedMessage.unsold();
            }

            User buyer = item.getCurrentBidder();
            User seller = item.getSeller();
            BigDecimal price = item.getNowPrice();

            if (buyer.getBalance().compareTo(price) < 0) {
                item.setStatus(ItemStatus.CANCELLED);
                itemRepository.save(item);
                log.info("拍卖 {} 买家余额不足，已流拍", itemId);
                return AuctionEndedMessage.unsold();
            }

            buyer.setBalance(buyer.getBalance().subtract(price));
            seller.setBalance(seller.getBalance().add(price));
            userRepository.save(buyer);
            userRepository.save(seller);

            Transaction transaction = new Transaction();
            transaction.setItem(item);
            transaction.setSeller(seller);
            transaction.setBuyer(buyer);
            transaction.setFinalPrice(price);
            transaction.setCompletedAt(now);
            transactionRepository.save(transaction);

            item.setStatus(ItemStatus.ENDED);
            itemRepository.save(item);

            log.info("拍卖 {} 成交：{} 以 {} 元从 {} 手中竞得",
                    itemId, buyer.getUsername(), price, seller.getUsername());

            return AuctionEndedMessage.sold(buyer.getUsername(), price, buyer.getId());
        }
    }
}
