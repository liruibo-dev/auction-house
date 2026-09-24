package com.auctionhouse.controller;

import com.auctionhouse.constant.SessionKeys;
import com.auctionhouse.dto.ApiResponse;
import com.auctionhouse.dto.BidRequest;
import com.auctionhouse.dto.BidResponse;
import com.auctionhouse.dto.ItemResponse;
import com.auctionhouse.entity.Bid;
import com.auctionhouse.service.BidService;
import com.auctionhouse.service.ItemService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/auctions")
public class BidController {

    private final BidService bidService;
    private final ItemService itemService;

    public BidController(BidService bidService, ItemService itemService) {
        this.bidService = bidService;
        this.itemService = itemService;
    }

    @PostMapping("/{id}/bid")
    public ApiResponse<ItemResponse> bid(@PathVariable Long id,
                                         @RequestBody BidRequest request,
                                         HttpSession session) {
        Long userId = (Long) session.getAttribute(SessionKeys.USER_ID);
        if (userId == null) {
            return ApiResponse.error(401, "请先登录");
        }
        bidService.placeBid(id, userId, request.getAmount());
        return ApiResponse.success("出价成功", ItemResponse.from(itemService.getItem(id)));
    }

    @GetMapping("/{id}/bids")
    public ApiResponse<List<BidResponse>> bids(@PathVariable Long id) {
        List<BidResponse> responses = new ArrayList<BidResponse>();
        for (Bid bid : bidService.getBidHistory(id)) {
            responses.add(BidResponse.from(bid));
        }
        return ApiResponse.success("ok", responses);
    }

    @GetMapping("/my/bids")
    public ApiResponse<List<BidResponse>> myBids(HttpSession session) {
        Long userId = (Long) session.getAttribute(SessionKeys.USER_ID);
        if (userId == null) {
            return ApiResponse.error(401, "请先登录");
        }
        List<BidResponse> responses = new ArrayList<BidResponse>();
        for (Bid bid : bidService.getMyBids(userId)) {
            responses.add(BidResponse.from(bid));
        }
        return ApiResponse.success("ok", responses);
    }
}
