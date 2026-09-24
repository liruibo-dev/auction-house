package com.auctionhouse.repository;

import com.auctionhouse.entity.Item;
import com.auctionhouse.entity.ItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {

    // 带 Pageable 的版本：排序和"取第几页"都由调用方通过 Pageable 传进来，
    // 方法名里的 OrderBy 就不需要了，两种排序条件（价格、结束时间）也不用手写两个方法
    Page<Item> findByStatus(ItemStatus status, Pageable pageable);

    Page<Item> findByStatusAndItemNameContaining(ItemStatus status, String keyword, Pageable pageable);

    List<Item> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    List<Item> findByStatusAndEndTimeBefore(ItemStatus status, LocalDateTime time);
}
