package com.auctionhouse.service.impl;

import com.auctionhouse.entity.Bid;
import com.auctionhouse.entity.Item;
import com.auctionhouse.entity.ItemStatus;
import com.auctionhouse.entity.User;
import com.auctionhouse.exception.InvalidBidException;
import com.auctionhouse.repository.BidRepository;
import com.auctionhouse.repository.ItemRepository;
import com.auctionhouse.repository.UserRepository;
import com.auctionhouse.service.BidService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发出价测试：50 个线程同时对同一场拍卖出价，看数据会不会被写坏。
 *
 * 这是整个项目唯一能回答"10 个人同时抢一个东西，系统会不会出错"的测试。
 * BidServiceImplTest 证明的是"规则写得对"，这个类证明的是"规则在抢的时候还成立"。
 *
 * ⚠️ 这个类故意没有 @Transactional，跟 BidServiceImplTest 不一样，原因有两个：
 *
 *   1. 造出来的卖家/买家/物品必须**真的提交**到数据库，50 个线程才看得见。
 *      测试方法上的 @Transactional 只作用于"跑测试的那个线程"，它的改动没提交，
 *      子线程各自开事务，根本读不到 —— 会全部报"拍卖不存在"。
 *
 *   2. 子线程的写入本来就回滚不了。@Transactional 是绑在线程上的，
 *      主线程回滚管不了别的线程。与其留个假的回滚承诺，不如自己收拾干净（见 tearDown）。
 *
 * 所以：造数据真提交 → 测完手动删。这是并发测试的代价。
 */
@SpringBootTest
@ActiveProfiles("test")
class BidConcurrencyTest {

    private static final int THREADS = 50;
    private static final int START_PRICE = 100;   // 物品当前价
    private static final int PRICE_STEP = 10;     // 第 i 个线程出价 = 100 + 10*i

    @Autowired
    private BidService bidService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private BidRepository bidRepository;

    private User seller;
    private Item item;
    private final List<User> bidders = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // 用户名带个纳秒后缀：万一上次跑完没清理干净，这次也不会撞 unique 约束
        String tag = String.valueOf(System.nanoTime());

        seller = saveUser("conc_seller_" + tag, "0");
        item = saveActiveItem(seller, START_PRICE);

        for (int i = 0; i < THREADS; i++) {
            // 每人给 100 万，保证余额永远不是瓶颈 —— 这场测试只考察并发，不考察余额
            bidders.add(saveUser("conc_bidder_" + tag + "_" + i, "1000000"));
        }
    }

    @AfterEach
    void tearDown() {
        // 删除顺序不能乱：bids 指向 item 和 users，item 指向 seller/currentBidder。
        // 必须先删指着别人的，再删被指的，否则外键约束会拦下来。
        if (item == null) {
            return;
        }
        bidRepository.deleteAll(bidRepository.findByItemIdOrderByBidTimeDesc(item.getId()));
        itemRepository.delete(item);
        userRepository.deleteAll(bidders);
        if (seller != null) {
            userRepository.delete(seller);
        }
    }

    @Test
    @DisplayName("50 线程同时抢：没有丢更新，冗余字段跟真实记录对得上")
    void concurrentBids_keepDataConsistent() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        // 发令枪：50 个线程submit进去之后全都卡在 await() 上，主线程 countDown() 那一下才一起冲
        CountDownLatch startGun = new CountDownLatch(1);
        // 终点线：主线程靠它等 50 个线程全部跑完
        CountDownLatch finishLine = new CountDownLatch(THREADS);

        AtomicInteger accepted = new AtomicInteger();                        // 出价被接受几次
        AtomicInteger rejected = new AtomicInteger();                        // 被更高价顶掉几次
        AtomicReference<Throwable> unexpected = new AtomicReference<>();     // 本不该出现的异常

        try {
            for (int i = 0; i < THREADS; i++) {
                User bidder = bidders.get(i);
                BigDecimal amount = new BigDecimal(START_PRICE + PRICE_STEP * (i + 1));

                pool.submit(() -> {
                    try {
                        startGun.await();   // 在这里等发令枪，而不是直接往下跑
                        bidService.placeBid(item.getId(), bidder.getId(), amount);
                        accepted.incrementAndGet();
                    } catch (InvalidBidException e) {
                        // 正常情况：自己喊的价已经被人超了，业务上的拒绝，不是 bug
                        rejected.incrementAndGet();
                    } catch (Throwable t) {
                        // 死锁、连接池超时、空指针之类，都会掉进这里 —— 一旦有，测试必挂
                        unexpected.compareAndSet(null, t);
                    } finally {
                        finishLine.countDown();
                    }
                });
            }

            startGun.countDown();   // 砰！50 个线程一起冲
            assertTrue(finishLine.await(60, TimeUnit.SECONDS), "有线程 60 秒还没跑完，八成是死锁了");

            if (unexpected.get() != null) {
                throw new AssertionError("出现了预期外的异常：" + unexpected.get(), unexpected.get());
            }

            // 每个线程要么成功要么被拒，加起来必须正好 50。
            // 少一个就说明某个线程静悄悄地挂了（异常被吞掉是最难查的一类 bug）。
            assertEquals(THREADS, accepted.get() + rejected.get(),
                    "50 个线程的结果加起来不等于 50，有线程没交代清楚自己干了什么");

            // 下面开始拿数据库里的事实说话
            List<Bid> rows = bidRepository.findByItemIdOrderByBidTimeDesc(item.getId());
            Item finalItem = itemRepository.findById(item.getId()).orElseThrow();

            // ① 成功几次就该有几条记录，一条不多一条不少
            assertEquals(accepted.get(), rows.size(),
                    "bids 记录数跟成功次数对不上");

            // ② 没有两条记录金额相同（同一笔钱被算了两次）
            Set<BigDecimal> distinctAmounts = new TreeSet<>();
            rows.forEach(row -> distinctAmounts.add(row.getAmount()));
            assertEquals(rows.size(), distinctAmounts.size(),
                    "出现了金额相同的出价记录");

            // ③ 冗余字段 bidCount 必须等于真实记录数 —— 这是丢更新的照妖镜。
            //    bidCount++ 是"读出来、加一、写回去"，两个线程同时读到 3 都写 4，
            //    记录会多一条，计数器却只涨了 1。锁要是没起作用，这条最先挂。
            assertEquals(rows.size(), finalItem.getBidCount(),
                    "bidCount 跟真实出价记录数对不上 —— 发生了丢更新");

            // ④ 当前价必须等于所有记录里的最高价（低价不能覆盖掉高价）
            BigDecimal maxAmount = rows.stream()
                    .map(Bid::getAmount)
                    .max(BigDecimal::compareTo)
                    .orElseThrow();
            assertEquals(0, maxAmount.compareTo(finalItem.getNowPrice()),
                    "当前价不是所有出价里的最高价 —— 发生了丢更新");

            // ⑤ 最高价那条记录的出价人，就是当前的最高出价者（成交后会变成买家，不能挂错人）
            Bid topBid = rows.stream()
                    .filter(row -> row.getAmount().compareTo(maxAmount) == 0)
                    .findFirst()
                    .orElseThrow();
            assertEquals(topBid.getBidder().getId(), finalItem.getCurrentBidder().getId(),
                    "最高出价者跟最高价记录的出价人对不上");

            // ⑥ 最高价 600 一定会成交：没有任何出价能比它高，所以它永远通得过"必须高于当前价"
            assertEquals(0, new BigDecimal("600").compareTo(finalItem.getNowPrice()),
                    "最高价没成交");

            // ⑦ 反过来说，50 个线程里不可能全成功 —— 价高者得，越晚喊的越贵
            assertTrue(accepted.get() >= 1, "一次都没成功，校验逻辑是不是把所有人都拒了");

            System.out.printf("%n===== 50 线程同时出价：成功 %d 次，被拒 %d 次，最终价 %s 元，bidCount=%d =====%n",
                    accepted.get(), rejected.get(), finalItem.getNowPrice(), finalItem.getBidCount());
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- 造数据用的小工具（跟 BidServiceImplTest 里那套一个思路）----------

    private User saveUser(String username, String balance) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash("not-a-real-hash");
        user.setBalance(new BigDecimal(balance));
        user.setCreatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    private Item saveActiveItem(User seller, int nowPrice) {
        Item item = new Item();
        item.setSeller(seller);
        item.setItemName("并发测试物品");
        item.setStartPrice(new BigDecimal(nowPrice));
        item.setNowPrice(new BigDecimal(nowPrice));
        // 离结束还有 1 小时：远远在防狙击的 2 分钟窗口外，不会触发延时，
        // 这样这个测试就只考察并发，不掺防狙击的行为
        item.setEndTime(LocalDateTime.now().plusHours(1));
        item.setStatus(ItemStatus.ACTIVE);
        item.setCreatedAt(LocalDateTime.now());
        return itemRepository.save(item);
    }
}
