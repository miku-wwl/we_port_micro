package com.weilai.portfolio.infrastructure.marketdata.strategy;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class RandomPricingStrategyTest {

    private final RandomPricingStrategy pricingStrategy = new RandomPricingStrategy();

    @Test
    void generatePrice_ShouldReturnPriceInValidRange() {
        // 测试多次生成价格，确保均在 [10.0, 1000.0] 区间内
        String testTicker = "TEST";
        for (int i = 0; i < 100; i++) {
            BigDecimal price = pricingStrategy.generatePrice(testTicker);

            // 验证价格不小于最小值
            assertTrue(price.compareTo(BigDecimal.valueOf(10.0)) >= 0,
                    "生成的价格不应小于10.0");
            // 验证价格不大于最大值
            assertTrue(price.compareTo(BigDecimal.valueOf(1000.0)) <= 0,
                    "生成的价格不应大于1000.0");
        }
    }

    @Test
    void generatePrice_ShouldHaveTwoDecimalPlaces() {
        // 测试价格精度是否为2位小数
        String testTicker = "TEST";
        BigDecimal price = pricingStrategy.generatePrice(testTicker);

        assertEquals(2, price.scale(),
                "生成的价格应保留2位小数");
    }

    @Test
    void generatePrice_ShouldReturnDifferentValuesOnMultipleCalls() {
        // 测试多次调用是否返回不同价格（概率上的随机性验证）
        String testTicker = "TEST";
        BigDecimal firstPrice = pricingStrategy.generatePrice(testTicker);
        BigDecimal secondPrice = pricingStrategy.generatePrice(testTicker);

        assertNotEquals(firstPrice, secondPrice,
                "多次调用应返回不同的随机价格");
    }
}