package com.auctionhouse.service.impl;

import com.auctionhouse.service.AuctionSettlementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守着 2026-09-24 修的那个隐患：测试环境下，"每 5 秒自动结算"的调度器必须不存在。
 *
 * 为什么要专门写个测试守着一件"没发生的事"？
 * 因为这类失效是**沉默的**——如果哪天有人顺手删掉配置里那行开关、或者改掉
 * AuctionSettlementServiceImpl 上的 @ConditionalOnProperty，测试照样全绿，
 * 然后你真实的 auction_house 数据被静悄悄地结算掉，而且不会有任何报错。
 * 只有写死的断言拦得住这种"没发生的事"。
 */
@SpringBootTest
@ActiveProfiles("test")
class SettlementSchedulerDisabledTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("测试环境：定时结算的调度器不该被装配进 Spring 容器")
    void schedulerIsNotWiredInTests() {
        assertTrue(context.getBeansOfType(AuctionSettlementService.class).isEmpty(),
                "定时结算的调度器被装配进来了！跑测试会去结算 auction_house 里的真实拍卖。"
                        + "检查 application-test.properties 里 auction.settlement.enabled=false 还在不在，"
                        + "以及 AuctionSettlementServiceImpl 上的 @ConditionalOnProperty 有没有被改掉。");
    }

    @Test
    @DisplayName("顺带确认：真正的结算逻辑（AuctionSettler）不受开关影响，随时可以测")
    void settlementLogicItselfIsStillAvailable() {
        // 被关掉的只是"每 5 秒自动跑一次"这个触发器。
        // 结算的本体在 AuctionSettler 里，它必须一直可用——
        // 否则以后写结算测试就只能靠等定时任务，那就没法测了。
        assertTrue(context.getBeansOfType(AuctionSettler.class).size() == 1,
                "AuctionSettler 应该还在容器里，写结算测试要用它");
    }
}
