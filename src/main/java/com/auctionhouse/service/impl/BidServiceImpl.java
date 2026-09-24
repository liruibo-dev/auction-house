package com.auctionhouse.service.impl;

import com.auctionhouse.component.AuctionLockRegistry;
import com.auctionhouse.dto.NewBidMessage;
import com.auctionhouse.dto.OutbidMessage;
import com.auctionhouse.entity.Bid;
import com.auctionhouse.entity.Item;
import com.auctionhouse.entity.ItemStatus;
import com.auctionhouse.entity.User;
import com.auctionhouse.exception.AuctionEndedException;
import com.auctionhouse.exception.InsufficientBalanceException;
import com.auctionhouse.exception.InvalidBidException;
import com.auctionhouse.exception.ItemNotFoundException;
import com.auctionhouse.repository.BidRepository;
import com.auctionhouse.repository.ItemRepository;
import com.auctionhouse.repository.UserRepository;
import com.auctionhouse.service.BidService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BidServiceImpl implements BidService {

    private static final long ANTI_SNIPE_WINDOW_MINUTES = 2;
    private static final long ANTI_SNIPE_EXTEND_MINUTES = 2;
    // 延时次数上限。没有上限的话，理论上可以一直卡在"结束前 2 分钟"反复出价，
    // 让这场拍卖永远结束不了——反狙击就变成了拒绝服务。
    private static final int MAX_SNIPE_EXTENDS = 5;

    private final ItemRepository itemRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;
    private final AuctionLockRegistry lockRegistry;
    private final SimpMessagingTemplate messagingTemplate;

    public BidServiceImpl(ItemRepository itemRepository,
                          BidRepository bidRepository,
                          UserRepository userRepository,
                          AuctionLockRegistry lockRegistry,
                          SimpMessagingTemplate messagingTemplate) {
        this.itemRepository = itemRepository;
        this.bidRepository = bidRepository;
        this.userRepository = userRepository;
        this.lockRegistry = lockRegistry;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public Bid placeBid(Long itemId, Long bidderId, BigDecimal amount) {
        // 同一场拍卖串行执行，不同拍卖互不阻塞；结算走的是同一把锁
        synchronized (lockRegistry.lockFor(itemId)) {
            BidResult result = doPlaceBid(itemId, bidderId, amount);
            // 推送留在锁内：消息顺序因此跟出价顺序一致，
            // 而且读到的价格和次数正好是这次出价的快照，不会被下一次出价改写
            pushBidMessages(result);
            return result.bid;
        }
    }

    private BidResult doPlaceBid(Long itemId, Long bidderId, BigDecimal amount) {
        LocalDateTime now = LocalDateTime.now();

        Item item = itemRepository.findById(itemId).orElse(null);
        if (item == null) {
            throw new ItemNotFoundException("拍卖不存在");
        }
        if (item.getStatus() != ItemStatus.ACTIVE || item.getEndTime().isBefore(now)) {
            throw new AuctionEndedException("拍卖已结束");
        }
        if (item.getSeller().getId().equals(bidderId)) {
            throw new InvalidBidException("不能竞拍自己发布的物品");
        }
        if (amount == null || amount.compareTo(item.getNowPrice()) <= 0) {
            throw new InvalidBidException("出价必须高于当前价 " + item.getNowPrice() + " 元");
        }
        if (item.getCurrentBidder() != null && item.getCurrentBidder().getId().equals(bidderId)) {
            throw new InvalidBidException("你已经是最高出价者了");
        }

        User bidder = userRepository.findById(bidderId).orElse(null);
        if (bidder == null) {
            throw new InvalidBidException("出价人不存在");
        }

        // 喊到（或超过）卖家设的一口价，就按一口价成交，不是按他喊的那个数
        boolean hitBuyNow = item.getBuyNowPrice() != null
                && amount.compareTo(item.getBuyNowPrice()) >= 0;
        BigDecimal dealPrice = hitBuyNow ? item.getBuyNowPrice() : amount;

        if (bidder.getBalance().compareTo(dealPrice) < 0) {
            throw new InsufficientBalanceException("余额不足，当前余额 " + bidder.getBalance() + " 元");
        }

        // 覆盖之前先把当前最高出价者记下来——他马上要被顶掉，得单独通知他一声
        User outbidUser = item.getCurrentBidder();

        Bid bid = new Bid();
        bid.setItem(item);
        bid.setBidder(bidder);
        bid.setAmount(dealPrice);
        bid.setBidTime(now);
        bidRepository.save(bid);

        item.setNowPrice(dealPrice);
        item.setCurrentBidder(bidder);
        item.setBidCount(item.getBidCount() + 1);

        if (hitBuyNow) {
            // 一口价买断：把结束时间提前到现在，下一轮定时结算（最多 5 秒后）自然会走完成交流程。
            // 这之后谁再出价都会被开头的"拍卖已结束"挡掉，所以不用另写一套成交逻辑。
            item.setEndTime(now);
        } else {
            extendIfSniping(item, now);
        }
        itemRepository.save(item);

        return new BidResult(bid, outbidUser == null ? null : outbidUser.getId());
    }

    // 出价成功后广播两件事：给这个房间所有围观者报新价，给刚被顶掉的人单独提个醒
    private void pushBidMessages(BidResult result) {
        Bid bid = result.bid;
        Item item = bid.getItem();

        NewBidMessage newBid = new NewBidMessage(
                bid.getBidder().getUsername(),
                bid.getAmount(),
                item.getEndTime().toString(),
                item.getBidCount());
        messagingTemplate.convertAndSend("/topic/auction/" + item.getId(), newBid);

        if (result.outbidUserId != null) {
            messagingTemplate.convertAndSend(
                    "/topic/auction/" + item.getId() + "/user/" + result.outbidUserId,
                    new OutbidMessage(bid.getAmount()));
        }
    }

    // 反狙击：结束前 2 分钟内有人出价，把结束时间往后推 2 分钟，但最多推 5 次
    private void extendIfSniping(Item item, LocalDateTime now) {
        if (item.getSnipeExtendCount() >= MAX_SNIPE_EXTENDS) {
            // 已经延满 5 次，这次出价照常成交，但时间不再往后推 —— 拍卖必须有个尽头
            return;
        }
        LocalDateTime windowStart = item.getEndTime().minusMinutes(ANTI_SNIPE_WINDOW_MINUTES);
        if (windowStart.isBefore(now)) {
            item.setEndTime(item.getEndTime().plusMinutes(ANTI_SNIPE_EXTEND_MINUTES));
            item.setSnipeExtendCount(item.getSnipeExtendCount() + 1);
        }
    }

    @Override
    public List<Bid> getBidHistory(Long itemId) {
        return bidRepository.findByItemIdOrderByBidTimeDesc(itemId);
    }

    @Override
    public List<Bid> getMyBids(Long bidderId) {
        return bidRepository.findByBidderIdOrderByBidTimeDesc(bidderId);
    }

    // 出价除了 Bid 本身，还得带出"被顶掉的是谁"，所以用这个小盒子把两样一起递出来
    private static final class BidResult {
        private final Bid bid;
        private final Long outbidUserId;

        private BidResult(Bid bid, Long outbidUserId) {
            this.bid = bid;
            this.outbidUserId = outbidUserId;
        }
    }
}
