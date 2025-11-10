package com.weilai.portfolio.infrastructure.marketdata.provider;

import com.weilai.portfolio.entity.MarketData;
import com.weilai.portfolio.infrastructure.marketdata.strategy.GBMPricingStrategy;
import com.weilai.portfolio.infrastructure.marketdata.strategy.PricingStrategy;
import com.weilai.portfolio.infrastructure.marketdata.strategy.RandomPricingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Random;

/**
 * 模拟市场数据提供者（仅推送股票价格，适配期权标的的定价依赖）
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReactiveMarketDataProvider {
    @Value("${portfolio.market-data.min-interval}")
    private long minInterval;
    @Value("${portfolio.market-data.max-interval}")
    private long maxInterval;
    @Value("${portfolio.market-data.pricing-strategy}")
    private String pricingStrategy;

    private final GBMPricingStrategy gbmPricingStrategy;
    private final RandomPricingStrategy randomPricingStrategy;
    private final Random random = new Random();
    private static final int PRICE_SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    // {"AAPL", "TELSA"};
    @Value("${portfolio.market-data.stock-tickers:}")
    private String[] STOCK_TICKERS;

    public Flux<MarketData> publishMarketData() {
        return Flux.fromArray(STOCK_TICKERS)
                .repeat() // 无限循环推送股票价格
                .flatMap(ticker -> {
                    // 选择定价策略（GBM/随机）
                    PricingStrategy strategy = "GBM".equals(pricingStrategy) ? gbmPricingStrategy : randomPricingStrategy;
                    BigDecimal stockPrice = strategy.generatePrice(ticker)
                            .setScale(PRICE_SCALE, ROUNDING_MODE);

                    // 封装股票市场数据（ticker 为股票代码，如 AAPL）
                    MarketData marketData = new MarketData(ticker, stockPrice, System.currentTimeMillis());

                    // 0.5-2秒随机间隔推送
                    long range = maxInterval - minInterval + 1;
                    long randomDelay;
                    if (range <= 0) {
                        randomDelay = minInterval; // 处理min > max的异常情况
                    } else {
                        // JDK 8兼容方案：用nextDouble()生成[0,1)的随机数，再映射到[0, range)范围
                        randomDelay = minInterval + (long) (random.nextDouble() * range);
                    }

                    return Mono.just(marketData)
                            .delayElement(Duration.ofMillis(randomDelay));
                })
                // TODO 这里的subscribeOn, 后续学习一下
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorContinue((e, t) -> log.error("股票[{}]价格推送失败", t, e));
    }
}