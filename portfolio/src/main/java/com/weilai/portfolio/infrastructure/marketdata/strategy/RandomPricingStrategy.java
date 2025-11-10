package com.weilai.portfolio.infrastructure.marketdata.strategy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

/**
 * 随机定价策略（简单模拟价格波动）
 */
@Component
public class RandomPricingStrategy implements PricingStrategy {
    private final Random random = new Random();
    // 价格区间（可配置，此处默认10-1000 USD）
    private static final BigDecimal MIN_PRICE = BigDecimal.valueOf(10.0);
    private static final BigDecimal MAX_PRICE = BigDecimal.valueOf(1000.0);

    @Override
    public BigDecimal generatePrice(String ticker) {
        // 生成区间内随机价格
        double randomPrice = MIN_PRICE.doubleValue() + random.nextDouble() * (MAX_PRICE.doubleValue() - MIN_PRICE.doubleValue());
        return BigDecimal.valueOf(randomPrice).setScale(2, RoundingMode.HALF_UP);
    }
}