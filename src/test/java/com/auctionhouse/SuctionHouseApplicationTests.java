package com.auctionhouse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * ⚠️ 新写的测试类都要记得加 @ActiveProfiles("test")。
 * 不加的话它会用线上配置启动 @Scheduled 定时任务，真去结算 auction_house 里的数据——
 * 详情见 AuctionSettlementServiceImpl 的类注释。
 */
@SpringBootTest
@ActiveProfiles("test")
class SuctionHouseApplicationTests {

	@Test
	void contextLoads() {
	}

}
