package com.auctionhouse.service.impl;

import com.auctionhouse.dto.AuctionEndedMessage;
import com.auctionhouse.entity.Item;
import com.auctionhouse.entity.ItemStatus;
import com.auctionhouse.repository.ItemRepository;
import com.auctionhouse.service.AuctionSettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 每 5 秒扫一次过期拍卖，交给 AuctionSettler 结算。
 *
 * ⚠️ 为什么整类挂在一个开关底下（2026-09-24 修的隐患）
 *
 * 起因：@SpringBootTest 会启动完整的 Spring 上下文，@EnableScheduling 也就跟着生效——
 * 于是**跑单元测试时会有一个"假 app"去扫真实的 auction_house 库，把过期拍卖真的结算掉**。
 * 麻烦的是 settleOne 用的是 REQUIRES_NEW：它自己开新事务并提交，
 * 测试方法上那个 @Transactional 根本回滚不了它。数据就这么真被改了。
 * 更巧的是如果那时你自己的 app 也在跑，等于同一时刻两个调度器在结算同一个库。
 *
 * 修法：把整个调度器 bean 挂到 auction.settlement.enabled 上，
 * 测试 profile 里显式关掉（见 src/test/resources/application-test.properties）。
 * matchIfMissing = true 表示"配置里没写这个开关时就当它是开着的"——
 * 所以线上 application.properties 一个字都不用改，行为跟修之前完全一样。
 *
 * 注意：关掉的只是"每 5 秒自动跑一次"这个触发器。
 * 真正的结算逻辑住在 AuctionSettler 里，那个 bean 不受影响——
 * 以后写结算测试，照样可以直接调 settler.settleOne()，不用等定时任务。
 */
@Service
@ConditionalOnProperty(name = "auction.settlement.enabled", havingValue = "true", matchIfMissing = true)
public class AuctionSettlementServiceImpl implements AuctionSettlementService {

    private static final Logger log = LoggerFactory.getLogger(AuctionSettlementServiceImpl.class);

    private final ItemRepository itemRepository;
    private final AuctionSettler settler;
    private final SimpMessagingTemplate messagingTemplate;

    public AuctionSettlementServiceImpl(ItemRepository itemRepository,
                                        AuctionSettler settler,
                                        SimpMessagingTemplate messagingTemplate) {
        this.itemRepository = itemRepository;
        this.settler = settler;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    @Scheduled(fixedDelay = 5000)
    public void settleEndedAuctions() {
        List<Item> expired = itemRepository.findByStatusAndEndTimeBefore(ItemStatus.ACTIVE, LocalDateTime.now());
        for (Item item : expired) {
            try {
                // 每场拍卖独立事务，一场失败不影响其他场
                AuctionEndedMessage ended = settler.settleOne(item.getId());
                if (ended != null) {
                    // 走到这里事务已经提交了，广播的成交消息不会跟数据库对不上
                    messagingTemplate.convertAndSend("/topic/auction/" + item.getId(), ended);
                }
            } catch (Exception e) {
                log.error("结算拍卖 {} 失败", item.getId(), e);
            }
        }
    }
}
