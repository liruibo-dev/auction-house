package com.auctionhouse.service.impl;

import com.auctionhouse.dto.CreateItemRequest;
import com.auctionhouse.entity.Item;
import com.auctionhouse.entity.ItemStatus;
import com.auctionhouse.entity.User;
import com.auctionhouse.exception.InvalidItemException;
import com.auctionhouse.exception.ItemNotFoundException;
import com.auctionhouse.repository.ItemRepository;
import com.auctionhouse.repository.UserRepository;
import com.auctionhouse.service.ItemService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ItemServiceImpl implements ItemService {

    // 每页最多给这么多条。不封顶的话，一个 size=100000 的请求就能让数据库去捞全表
    private static final int MAX_PAGE_SIZE = 100;

    private final ItemRepository itemRepository;
    private final UserRepository userRepository;

    public ItemServiceImpl(ItemRepository itemRepository, UserRepository userRepository) {
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Item createItem(Long sellerId, CreateItemRequest request) {
        if (request.getItemName() == null || request.getItemName().trim().isEmpty()) {
            throw new InvalidItemException("物品名称不能为空");
        }
        if (request.getStartPrice() == null || request.getStartPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidItemException("起拍价必须大于 0");
        }
        if (request.getBuyNowPrice() != null
                && request.getBuyNowPrice().compareTo(request.getStartPrice()) <= 0) {
            throw new InvalidItemException("一口价必须高于起拍价");
        }
        if (request.getDurationHours() == null || request.getDurationHours() <= 0) {
            throw new InvalidItemException("拍卖时长必须大于 0 小时");
        }

        User seller = userRepository.findById(sellerId).orElse(null);
        if (seller == null) {
            throw new ItemNotFoundException("卖家不存在");
        }

        LocalDateTime now = LocalDateTime.now();

        Item item = new Item();
        item.setSeller(seller);
        item.setItemName(request.getItemName().trim());
        item.setDescription(request.getDescription());
        item.setStartPrice(request.getStartPrice());
        item.setBuyNowPrice(request.getBuyNowPrice());
        item.setNowPrice(request.getStartPrice());
        item.setBidCount(0);
        item.setStatus(ItemStatus.ACTIVE);
        item.setCreatedAt(now);
        item.setEndTime(now.plusHours(request.getDurationHours()));

        return itemRepository.save(item);
    }

    @Override
    public Page<Item> getActiveItems(String keyword, int page, int size) {
        // 页码不能是负数，每页条数要封顶，这两个值是从 URL 参数直接来的，不能信
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        // 按结束时间升序：眼看就要结束的排在最前面
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("endTime").ascending());

        if (keyword == null || keyword.trim().isEmpty()) {
            return itemRepository.findByStatus(ItemStatus.ACTIVE, pageable);
        }
        return itemRepository.findByStatusAndItemNameContaining(ItemStatus.ACTIVE, keyword.trim(), pageable);
    }

    @Override
    public List<Item> getMyItems(Long sellerId) {
        return itemRepository.findBySellerIdOrderByCreatedAtDesc(sellerId);
    }

    @Override
    public Item getItem(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException("拍卖不存在"));
    }

    @Override
    public Item cancelItem(Long itemId, Long userId) {
        Item item = getItem(itemId);

        if (!item.getSeller().getId().equals(userId)) {
            throw new InvalidItemException("只有卖家本人可以取消这场拍卖");
        }
        if (item.getStatus() != ItemStatus.ACTIVE) {
            throw new InvalidItemException("只有进行中的拍卖可以取消");
        }
        if (item.getBidCount() > 0) {
            throw new InvalidItemException("已经有人出价，不能取消");
        }

        item.setStatus(ItemStatus.CANCELLED);
        return itemRepository.save(item);
    }
}
