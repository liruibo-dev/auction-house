package com.auctionhouse.service.impl;

import com.auctionhouse.entity.Item;
import com.auctionhouse.entity.ItemStatus;
import com.auctionhouse.entity.User;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防狙击延时的上限测试。
 *
 * 为什么要有上限：没有上限的话，"结束前 2 分钟内出价就 +2 分钟"这条规则可以被人反复触发——
 * 他一出价时间就往后走，别人永远等不到结束。反狙击本意是保护出价者，没有上限就变成了拒绝服务。
 * 现在规则是：最多延 5 次，第 6 次照常成交，但时间不再往后走。
 *
 * 这个测试是单线程的，所以可以用 @Transactional 自动回滚（对比 BidConcurrencyTest 和
 * AuctionSettlementTest：那两个必须真提交、手动清理，原因各写在各自的类注释里）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AntiSnipeExtendTest {

    /** 跟 BidServiceImpl.MAX_SNIPE_EXTENDS 对应。改那边的话这里也要改。 */
    private static final int MAX_EXTENDS = 5;

    @Autowired
    private BidService bidService;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;

    private User seller;
    private final List<User> bidders = new ArrayList<>();

    @BeforeEach
    void setUp() {
        seller = saveUser("snipe_seller", "0");
        // 要出价 6 次（5 次触发延时 + 1 次验证不再延），所以需要 6 个不同的人：
        // 同一个人不能连续出价（"不能自己顶自己"那条校验会拦下来）
        for (int i = 0; i < MAX_EXTENDS + 1; i++) {
            bidders.add(saveUser("snipe_bidder_" + i, "1000"));
        }
    }

    @Test
    @DisplayName("反狙击最多延时 5 次：前 5 次各推 2 分钟，第 6 次不再推")
    void extendsAtMostFiveTimes() {
        Item item = saveActiveItem(seller);

        for (int i = 0; i < MAX_EXTENDS + 1; i++) {
            // 关键的一步：每次出价前把结束时间硬拽回"距现在 30 秒"，逼这次出价落在 2 分钟窗口里。
            //
            // 为什么必须手动拽？因为延时的效果是把结束时间推到 2 分钟之外，
            // 于是"结束前 2 分钟"这个窗口当场就不成立了 —— 下一次出价自然触发不了延时。
            // 真实世界里这意味着要再等 2 分钟才能触发第二次；这里直接把时间拨回去，省掉等待。
            LocalDateTime pushedBack = LocalDateTime.now().plusSeconds(30);
            item.setEndTime(pushedBack);
            item = itemRepository.save(item);

            bidService.placeBid(item.getId(), bidders.get(i).getId(), new BigDecimal(110 + i * 10));

            Item after = reload(item.getId());
            long pushedSeconds = Duration.between(pushedBack, after.getEndTime()).getSeconds();

            if (i < MAX_EXTENDS) {
                assertTrue(pushedSeconds >= 100,
                        "第 " + (i + 1) + " 次出价应该把结束时间推后约 120 秒，实际只推了 " + pushedSeconds + " 秒");
            } else {
                assertTrue(pushedSeconds <= 5,
                        "第 " + (i + 1) + " 次出价不该再延时了（上限 " + MAX_EXTENDS
                                + " 次），实际又推了 " + pushedSeconds + " 秒");
            }
        }

        Item finalItem = reload(item.getId());
        assertEquals(MAX_EXTENDS, finalItem.getSnipeExtendCount(),
                "延时计数器应该正好停在 " + MAX_EXTENDS);
        assertEquals(MAX_EXTENDS + 1, finalItem.getBidCount(),
                "第 6 次出价虽然不延时，但出价本身必须照常成功");
    }

    @Test
    @DisplayName("不在窗口内（离结束还早）出价：不延时，计数器也不动")
    void doesNotExtendWhenFarFromEnd() {
        Item item = saveActiveItem(seller);
        LocalDateTime originalEnd = item.getEndTime();

        bidService.placeBid(item.getId(), bidders.get(0).getId(), new BigDecimal("110"));

        Item after = reload(item.getId());
        assertEquals(0, after.getSnipeExtendCount(), "离结束还早，不该触发延时");

        // 注意这里为什么用"秒级容差"而不是直接比两个 LocalDateTime 相等：
        // Java 的时间戳是纳秒精度，MySQL 的 DATETIME(6) 只存到微秒，
        // 插进去的时候会被四舍五入，读回来就跟存进去的差了不到 1 微秒。
        // 用 equals/compareTo 精确比，会被这点误差绊倒，报一个跟业务毫无关系的失败。
        // 判断"时间有没有被推动"要看量级，不看最后一个微秒。
        long movedSeconds = Duration.between(originalEnd, after.getEndTime()).getSeconds();
        assertTrue(movedSeconds < 60,
                "结束时间被推后了 " + movedSeconds + " 秒，但它离结束还有整整 1 小时，防狙击不该触发");
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

    /** 造一个"离结束还有 1 小时"的拍卖 —— 起始状态在 2 分钟窗口之外 */
    private Item saveActiveItem(User seller) {
        Item item = new Item();
        item.setSeller(seller);
        item.setItemName("防狙击测试物品");
        item.setStartPrice(new BigDecimal("100"));
        item.setNowPrice(new BigDecimal("100"));
        item.setEndTime(LocalDateTime.now().plusHours(1));
        item.setStatus(ItemStatus.ACTIVE);
        item.setCreatedAt(LocalDateTime.now());
        return itemRepository.save(item);
    }

    private Item reload(Long itemId) {
        entityManager.flush();
        entityManager.clear();
        return itemRepository.findById(itemId).orElseThrow();
    }
}
