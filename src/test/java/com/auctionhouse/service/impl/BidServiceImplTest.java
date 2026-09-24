package com.auctionhouse.service.impl;

import com.auctionhouse.entity.Bid;
import com.auctionhouse.entity.Item;
import com.auctionhouse.entity.ItemStatus;
import com.auctionhouse.entity.User;
import com.auctionhouse.exception.AuctionEndedException;
import com.auctionhouse.exception.InsufficientBalanceException;
import com.auctionhouse.exception.InvalidBidException;
import com.auctionhouse.repository.BidRepository;
import com.auctionhouse.repository.ItemRepository;
import com.auctionhouse.repository.UserRepository;
import com.auctionhouse.service.BidService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 出价的单线程校验测试：把 BidServiceImpl 里那五道 if 逐条钉死。
 *
 * 为什么是"单线程"？因为这一层只回答一个问题——规则本身写对了吗。
 * "10 个人同时点会不会出错"是另一个问题，要用并发测试回答，不在这里。
 */
@SpringBootTest
@ActiveProfiles("test") // 挂上测试专用配置：关掉刷屏的 SQL 日志
@Transactional // 每个测试方法跑完自动回滚，不会往 auction_house 里留垃圾数据
class BidServiceImplTest {

    @Autowired
    private BidService bidService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private BidRepository bidRepository;
    @Autowired
    private EntityManager entityManager;

    private User seller;
    private User bidder;

    // 每个测试方法开跑前都重新造一遍人，避免测试之间互相影响
    @BeforeEach
    void setUp() {
        seller = saveUser("test_seller", "0");
        bidder = saveUser("test_bidder", "1000");
    }

    @Test
    @DisplayName("正常出价：出价记录落库，当前价、出价次数、最高出价者都跟着更新")
    void placeBid_success() {
        Item item = saveActiveItem(seller, "100", LocalDateTime.now().plusHours(1));

        bidService.placeBid(item.getId(), bidder.getId(), new BigDecimal("200"));

        // 断言一：bids 表里真的多了一条 200 的记录
        List<Bid> bids = bidRepository.findByItemIdOrderByBidTimeDesc(item.getId());
        assertEquals(1, bids.size());
        assertEquals(0, new BigDecimal("200").compareTo(bids.get(0).getAmount()));

        // 断言二：items 表上那三个冗余字段也跟着动了
        Item reloaded = reload(item.getId());
        assertEquals(0, new BigDecimal("200").compareTo(reloaded.getNowPrice()));
        assertEquals(1, reloaded.getBidCount());
        assertEquals(bidder.getId(), reloaded.getCurrentBidder().getId());
    }

    @Test
    @DisplayName("出价没超过当前价：拒绝（刚好等于当前价也不行）")
    void placeBid_notHigherThanNowPrice_isRejected() {
        Item item = saveActiveItem(seller, "100", LocalDateTime.now().plusHours(1));

        assertThrows(InvalidBidException.class,
                () -> bidService.placeBid(item.getId(), bidder.getId(), new BigDecimal("100")));
    }

    @Test
    @DisplayName("余额不够：拒绝（这条分支之前一次都没走到过）")
    void placeBid_insufficientBalance_isRejected() {
        User poor = saveUser("test_poor", "50");
        Item item = saveActiveItem(seller, "100", LocalDateTime.now().plusHours(1));

        assertThrows(InsufficientBalanceException.class,
                () -> bidService.placeBid(item.getId(), poor.getId(), new BigDecimal("200")));
    }

    @Test
    @DisplayName("卖家不能竞拍自己发布的物品：拒绝")
    void placeBid_sellerCannotBid() {
        Item item = saveActiveItem(seller, "100", LocalDateTime.now().plusHours(1));

        assertThrows(InvalidBidException.class,
                () -> bidService.placeBid(item.getId(), seller.getId(), new BigDecimal("200")));
    }

    @Test
    @DisplayName("已经是最高出价者了：拒绝（防止连点按钮刷出一堆记录）")
    void placeBid_cannotOutbidYourself() {
        Item item = saveActiveItem(seller, "100", LocalDateTime.now().plusHours(1));

        bidService.placeBid(item.getId(), bidder.getId(), new BigDecimal("200"));

        assertThrows(InvalidBidException.class,
                () -> bidService.placeBid(item.getId(), bidder.getId(), new BigDecimal("300")));
    }

    @Test
    @DisplayName("过了结束时间：拒绝，哪怕状态还没来得及被定时任务改成 ENDED")
    void placeBid_afterEndTime_isRejected() {
        Item item = saveActiveItem(seller, "100", LocalDateTime.now().minusSeconds(1));

        assertThrows(AuctionEndedException.class,
                () -> bidService.placeBid(item.getId(), bidder.getId(), new BigDecimal("200")));
    }

    // ---------- 造数据用的小工具 ----------

    private User saveUser(String username, String balance) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash("not-a-real-hash");
        user.setBalance(new BigDecimal(balance));
        user.setCreatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    private Item saveActiveItem(User seller, String nowPrice, LocalDateTime endTime) {
        Item item = new Item();
        item.setSeller(seller);
        item.setItemName("测试物品");
        item.setStartPrice(new BigDecimal(nowPrice));
        item.setNowPrice(new BigDecimal(nowPrice));
        item.setEndTime(endTime);
        item.setStatus(ItemStatus.ACTIVE);
        item.setCreatedAt(LocalDateTime.now());
        return itemRepository.save(item);
    }

    /**
     * flush：把内存里的改动真正推给数据库
     * clear：把一级缓存清空
     * 两步做完再 findById，读到的才是数据库里的行，而不是刚才被改过的那个对象。
     * 少了这两步，断言等于"我问刚才那个对象你是不是 200"，它当然说是。
     */
    private Item reload(Long itemId) {
        entityManager.flush();
        entityManager.clear();
        return itemRepository.findById(itemId).orElseThrow();
    }
}
