package com.auctionhouse.service.impl;

import com.auctionhouse.dto.AuctionEndedMessage;
import com.auctionhouse.entity.Bid;
import com.auctionhouse.entity.Item;
import com.auctionhouse.entity.ItemStatus;
import com.auctionhouse.entity.Transaction;
import com.auctionhouse.entity.User;
import com.auctionhouse.repository.BidRepository;
import com.auctionhouse.repository.ItemRepository;
import com.auctionhouse.repository.TransactionRepository;
import com.auctionhouse.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 结算测试：拍卖到期后，钱怎么动、状态怎么变、成交记录怎么落。
 *
 * 这是整个项目唯一一段"改别人钱包"的代码，也是最不该出错的一段 —— 扣错一分钱都比出价出bug严重。
 * 之前它一次都没被测过（只在真实运行中被触发过两次）。
 *
 * ⚠️ 这个类故意没有 @Transactional，理由跟 BidConcurrencyTest 不一样，是**第三个**原因：
 *
 *   settleOne 上标的是 @Transactional(propagation = REQUIRES_NEW)。
 *   REQUIRES_NEW 的意思是"挂起当前事务，另开一个新的，并且自己提交"。
 *   于是：① 测试造的数据如果还在未提交的事务里，那个新事务在另一条连接上，根本看不见 → 会报"拍卖不存在"；
 *        ② 它提交之后，测试的事务无论怎么回滚都撤不回来 → 会留下假的成交记录和假的余额变动。
 *
 *   所以这里必须：造数据真提交 → 测完手动删。
 */
@SpringBootTest
@ActiveProfiles("test")
class AuctionSettlementTest {

    @Autowired
    private AuctionSettler settler;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private BidRepository bidRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    private User seller;
    private User buyer;
    private Item item;

    @BeforeEach
    void setUp() {
        String tag = String.valueOf(System.nanoTime());
        seller = saveUser("settle_seller_" + tag, "0");
        buyer = saveUser("settle_buyer_" + tag, "500");
    }

    @AfterEach
    void tearDown() {
        // 删除顺序：指着别人的先删。bids 和 transactions 都引用 item 和 users，item 又引用 users
        if (item != null) {
            bidRepository.deleteAll(bidRepository.findByItemIdOrderByBidTimeDesc(item.getId()));
            if (buyer != null) {
                transactionRepository.deleteAll(
                        transactionRepository.findByBuyerIdOrderByCompletedAtDesc(buyer.getId()));
            }
            itemRepository.delete(item);
        }
        if (buyer != null) {
            userRepository.delete(buyer);
        }
        if (seller != null) {
            userRepository.delete(seller);
        }
    }

    // ---------- 分支一：有出价、买家余额够 → 成交 ----------

    @Test
    @DisplayName("到期有出价且余额充足：状态转 ENDED，钱从买家到卖家，落一条成交记录")
    void settlesWithWinner() {
        item = saveExpiredItem(new BigDecimal("100"), buyer, 1);

        AuctionEndedMessage ended = settler.settleOne(item.getId());

        assertNotNull(ended, "成交应该返回一条要广播的消息");
        assertEquals(buyer.getUsername(), ended.getWinnerName());
        assertEquals(buyer.getId(), ended.getBuyerId());
        assertAmount("100", ended.getFinalPrice(), "广播里的成交价不对");

        Item settled = reloadItem();
        assertEquals(ItemStatus.ENDED, settled.getStatus(), "成交后状态应该是 ENDED");

        // 钱必须一分不差：买家 500 - 100 = 400，卖家 0 + 100 = 100
        assertAmount("400", reloadBalance(buyer), "买家余额扣错了");
        assertAmount("100", reloadBalance(seller), "卖家余额加错了");

        List<Transaction> transactions =
                transactionRepository.findByBuyerIdOrderByCompletedAtDesc(buyer.getId());
        assertEquals(1, transactions.size(), "应该正好落一条成交记录");
        Transaction transaction = transactions.get(0);
        assertAmount("100", transaction.getFinalPrice(), "成交记录里的价格不对");
        assertEquals(buyer.getId(), transaction.getBuyer().getId());
        assertEquals(seller.getId(), transaction.getSeller().getId());
    }

    // ---------- 分支二：无人出价 → 流拍 ----------

    @Test
    @DisplayName("到期无人出价：转 CANCELLED，不产生成交记录，双方余额不动")
    void cancelsWhenNobodyBid() {
        item = saveExpiredItem(new BigDecimal("100"), null, 0);

        AuctionEndedMessage ended = settler.settleOne(item.getId());

        assertNotNull(ended, "流拍也要广播一条消息（告诉围观的人这场结束了）");
        assertNull(ended.getWinnerName(), "流拍没有赢家");

        assertEquals(ItemStatus.CANCELLED, reloadItem().getStatus(), "无人出价应该流拍");
        assertAmount("500", reloadBalance(buyer), "流拍了不该动买家的钱");
        assertAmount("0", reloadBalance(seller), "流拍了不该动卖家的钱");
        assertEquals(0, transactionRepository.findByBuyerIdOrderByCompletedAtDesc(buyer.getId()).size(),
                "流拍不该产生成交记录");
    }

    // ---------- 分支三：有人出价但买家余额不够 → 流拍 ----------

    @Test
    @DisplayName("买家余额不够：流拍，钱一分不动（这就是'出价时不冻结余额'要付的代价）")
    void cancelsWhenBuyerCannotAfford() {
        // 买家一共 500，但先花掉 480，只剩 20，不够付 100 的成交价
        buyer.setBalance(new BigDecimal("20"));
        userRepository.save(buyer);
        item = saveExpiredItem(new BigDecimal("100"), buyer, 1);

        AuctionEndedMessage ended = settler.settleOne(item.getId());

        assertNull(ended.getWinnerName(), "付不起钱就不该有赢家");
        assertEquals(ItemStatus.CANCELLED, reloadItem().getStatus());
        assertAmount("20", reloadBalance(buyer), "流拍了不该扣买家的钱");
        assertAmount("0", reloadBalance(seller), "流拍了不该给卖家加钱");
        assertEquals(0, transactionRepository.findByBuyerIdOrderByCompletedAtDesc(buyer.getId()).size(),
                "流拍不该产生成交记录");
    }

    // ---------- 分支四：还没到期 → 什么都不做 ----------

    @Test
    @DisplayName("还没到结束时间：返回 null，状态原封不动")
    void doesNothingBeforeEndTime() {
        item = saveActiveItem(new BigDecimal("100"), buyer, 1, LocalDateTime.now().plusMinutes(5));

        AuctionEndedMessage ended = settler.settleOne(item.getId());

        assertNull(ended, "没到期不该返回要广播的消息");
        assertEquals(ItemStatus.ACTIVE, reloadItem().getStatus(), "没到期不该改状态");
        assertAmount("500", reloadBalance(buyer), "没到期不该动钱");
        assertEquals(0, transactionRepository.findByBuyerIdOrderByCompletedAtDesc(buyer.getId()).size());
    }

    // ---------- 分支五：重复结算 → 只算一次 ----------

    @Test
    @DisplayName("同一场拍卖结算两次：第二次什么都不做，钱只能扣一次")
    void doesNotSettleTwice() {
        item = saveExpiredItem(new BigDecimal("100"), buyer, 1);

        AuctionEndedMessage first = settler.settleOne(item.getId());
        assertNotNull(first, "第一次结算应该成交");

        // 模拟"两个调度器同时扫到这场拍卖"（2026-09-24 真的发生过：app 在跑的时候又跑测试）
        AuctionEndedMessage second = settler.settleOne(item.getId());

        assertNull(second, "已经结算过的拍卖再结算必须直接返回 null");
        assertAmount("400", reloadBalance(buyer), "钱被扣了两次！");
        assertAmount("100", reloadBalance(seller), "钱被加了两次！");
        assertEquals(1, transactionRepository.findByBuyerIdOrderByCompletedAtDesc(buyer.getId()).size(),
                "成交记录被写了两条！");
    }

    // ---------- 造数据 / 读数据的小工具 ----------

    private User saveUser(String username, String balance) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash("not-a-real-hash");
        user.setBalance(new BigDecimal(balance));
        user.setCreatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    private Item saveExpiredItem(BigDecimal price, User bidder, int bidCount) {
        // 结束时间设成 1 秒前 —— 已经到期，等着被结算
        return saveActiveItem(price, bidder, bidCount, LocalDateTime.now().minusSeconds(1));
    }

    private Item saveActiveItem(BigDecimal price, User bidder, int bidCount, LocalDateTime endTime) {
        Item item = new Item();
        item.setSeller(seller);
        item.setItemName("结算测试物品");
        item.setStartPrice(price);
        item.setNowPrice(price);
        item.setEndTime(endTime);
        item.setStatus(ItemStatus.ACTIVE);
        item.setCurrentBidder(bidder);
        item.setBidCount(bidCount);
        item.setCreatedAt(LocalDateTime.now());
        item = itemRepository.save(item);

        if (bidder != null) {
            Bid bid = new Bid();
            bid.setItem(item);
            bid.setBidder(bidder);
            bid.setAmount(price);
            bid.setBidTime(LocalDateTime.now());
            bidRepository.save(bid);
        }
        return item;
    }

    /**
     * 结算是在**它自己的事务里**提交的，所以我们手上那个 buyer 对象是结算之前的旧快照。
     * 必须重新查一次数据库，看到的才是结算后的真实余额 —— 直接断言旧对象等于自己骗自己。
     */
    private BigDecimal reloadBalance(User user) {
        return userRepository.findById(user.getId()).orElseThrow().getBalance();
    }

    private Item reloadItem() {
        return itemRepository.findById(item.getId()).orElseThrow();
    }

    private void assertAmount(String expected, BigDecimal actual, String message) {
        // 金额一律用 compareTo 比，不能用 equals：数据库 DECIMAL(10,2) 还回来的是 400.00，
        // 跟 new BigDecimal("400") 用 equals 比会返回 false（BigDecimal 连精度一起比）
        assertEquals(0, new BigDecimal(expected).compareTo(actual), message);
    }
}
