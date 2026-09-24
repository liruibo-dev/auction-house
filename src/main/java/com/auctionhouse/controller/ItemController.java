package com.auctionhouse.controller;

import com.auctionhouse.constant.SessionKeys;
import com.auctionhouse.dto.ApiResponse;
import com.auctionhouse.dto.CreateItemRequest;
import com.auctionhouse.dto.ItemResponse;
import com.auctionhouse.dto.PageResponse;
import com.auctionhouse.entity.Item;
import com.auctionhouse.service.ItemService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/auctions")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ItemResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Item> result = itemService.getActiveItems(keyword, page, size);

        List<ItemResponse> responses = new ArrayList<ItemResponse>();
        for (Item item : result.getContent()) {
            responses.add(ItemResponse.from(item));
        }

        PageResponse<ItemResponse> data = PageResponse.of(
                responses, result.getNumber(), result.getTotalPages(), result.getTotalElements());
        return ApiResponse.success("ok", data);
    }

    @GetMapping("/{id}")
    public ApiResponse<ItemResponse> detail(@PathVariable Long id) {
        return ApiResponse.success("ok", ItemResponse.from(itemService.getItem(id)));
    }

    @PostMapping
    public ApiResponse<ItemResponse> create(@RequestBody CreateItemRequest request, HttpSession session) {
        Long userId = (Long) session.getAttribute(SessionKeys.USER_ID);
        if (userId == null) {
            return ApiResponse.error(401, "请先登录");
        }
        Item item = itemService.createItem(userId, request);
        return ApiResponse.success("发布成功", ItemResponse.from(item));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id, HttpSession session) {
        Long userId = (Long) session.getAttribute(SessionKeys.USER_ID);
        if (userId == null) {
            return ApiResponse.error(401, "请先登录");
        }
        itemService.cancelItem(id, userId);
        return ApiResponse.success("已取消", null);
    }

    @GetMapping("/mine")
    public ApiResponse<List<ItemResponse>> mine(HttpSession session) {
        Long userId = (Long) session.getAttribute(SessionKeys.USER_ID);
        if (userId == null) {
            return ApiResponse.error(401, "请先登录");
        }
        List<ItemResponse> responses = new ArrayList<ItemResponse>();
        for (Item item : itemService.getMyItems(userId)) {
            responses.add(ItemResponse.from(item));
        }
        return ApiResponse.success("ok", responses);
    }
}
