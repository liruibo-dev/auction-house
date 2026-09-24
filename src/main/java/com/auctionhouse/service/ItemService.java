package com.auctionhouse.service;

import com.auctionhouse.dto.CreateItemRequest;
import com.auctionhouse.entity.Item;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ItemService {

    Item createItem(Long sellerId, CreateItemRequest request);

    Page<Item> getActiveItems(String keyword, int page, int size);

    List<Item> getMyItems(Long sellerId);

    Item getItem(Long itemId);

    Item cancelItem(Long itemId, Long userId);
}
