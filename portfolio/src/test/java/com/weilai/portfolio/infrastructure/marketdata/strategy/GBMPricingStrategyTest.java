package com.weilai.portfolio.infrastructure.marketdata.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GBMPricingStrategyTest {

    private GBMPricingStrategy gbmPricingStrategy;

    @Mock
    private Random mockRandom;

    @BeforeEach
    void setUp() {
        gbmPricingStrategy = new GBMPricingStrategy();

        ReflectionTestUtils.setField(gbmPricingStrategy, "muAAPL", 0.08);
        ReflectionTestUtils.setField(gbmPricingStrategy, "muTELSA", 0.12);
        ReflectionTestUtils.setField(gbmPricingStrategy, "sigmaAAPL", 0.2);
        ReflectionTestUtils.setField(gbmPricingStrategy, "sigmaTELSA", 0.3);
        ReflectionTestUtils.setField(gbmPricingStrategy, "initialPriceAAPL", new BigDecimal("110.0"));
        ReflectionTestUtils.setField(gbmPricingStrategy, "initialPriceTELSA", new BigDecimal("450.0"));
        ReflectionTestUtils.setField(gbmPricingStrategy, "random", mockRandom);

        Map<String, Long> lastTimestampMap = (Map<String, Long>) ReflectionTestUtils.getField(gbmPricingStrategy, "lastTimestampMap");
        Map<String, BigDecimal> lastPriceMap = (Map<String, BigDecimal>) ReflectionTestUtils.getField(gbmPricingStrategy, "lastPriceMap");
        lastTimestampMap.clear();
        lastPriceMap.clear();
    }

    @Test
    void generatePrice_FirstCall_ReturnsInitialPrice() {
        BigDecimal aaplInitial = gbmPricingStrategy.generatePrice("aapl");
        assertEquals(new BigDecimal("110.00").setScale(2, RoundingMode.HALF_UP), aaplInitial);

        BigDecimal telsaInitial = gbmPricingStrategy.generatePrice("TELSA");
        assertEquals(new BigDecimal("450.00").setScale(2, RoundingMode.HALF_UP), telsaInitial);

        BigDecimal defaultInitial = gbmPricingStrategy.generatePrice("GOOG");
        assertEquals(new BigDecimal("100.00"), defaultInitial.setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    void getMuAndSigma_ReturnsCorrectValues() {
        double aaplMu = (double) ReflectionTestUtils.invokeMethod(gbmPricingStrategy, "getMu", "AAPL");
        double aaplSigma = (double) ReflectionTestUtils.invokeMethod(gbmPricingStrategy, "getSigma", "AAPL");
        assertEquals(0.08, aaplMu, 0.001);
        assertEquals(0.2, aaplSigma, 0.001);

        double telsaMu = (double) ReflectionTestUtils.invokeMethod(gbmPricingStrategy, "getMu", "TELSA");
        double telsaSigma = (double) ReflectionTestUtils.invokeMethod(gbmPricingStrategy, "getSigma", "TELSA");
        assertEquals(0.12, telsaMu, 0.001);
        assertEquals(0.3, telsaSigma, 0.001);

        double defaultMu = (double) ReflectionTestUtils.invokeMethod(gbmPricingStrategy, "getMu", "GOOG");
        double defaultSigma = (double) ReflectionTestUtils.invokeMethod(gbmPricingStrategy, "getSigma", "GOOG");
        assertEquals(0.05, defaultMu, 0.001);
        assertEquals(0.2, defaultSigma, 0.001);
    }

    @Test
    void generatePrice_GBMFormula_CalculatesCorrectly() {
        String ticker = "AAPL";
        gbmPricingStrategy.generatePrice(ticker);

        when(mockRandom.nextGaussian()).thenReturn(0.0);
        long deltaTSeconds = 3600;
        Map<String, Long> lastTimestampMap = (Map<String, Long>) ReflectionTestUtils.getField(gbmPricingStrategy, "lastTimestampMap");
        long lastTimestamp = lastTimestampMap.get(ticker);
        lastTimestampMap.put(ticker, lastTimestamp - deltaTSeconds * 1000);

        BigDecimal newPrice = gbmPricingStrategy.generatePrice(ticker);
        assertEquals(new BigDecimal("110.00"), newPrice);
    }

    @Test
    void generatePrice_PriceIsNonNegativeAndScaled() {
        String ticker = "AAPL";
        gbmPricingStrategy.generatePrice(ticker);
        when(mockRandom.nextGaussian()).thenReturn(-100.0);
        Map<String, Long> lastTimestampMap = (Map<String, Long>) ReflectionTestUtils.getField(gbmPricingStrategy, "lastTimestampMap");
        long lastTimestamp = lastTimestampMap.get(ticker);
        lastTimestampMap.put(ticker, lastTimestamp - 3600 * 1000);

        BigDecimal price = gbmPricingStrategy.generatePrice(ticker);
        assertTrue(price.compareTo(new BigDecimal("0.01")) >= 0, "价格不能小于0.01");
        assertEquals(2, price.scale(), "价格应保留两位小数");

        when(mockRandom.nextGaussian()).thenReturn(0.5);
        price = gbmPricingStrategy.generatePrice(ticker);
        assertEquals(2, price.scale());
    }

    @Test
    void generatePrice_UpdatesCache() throws InterruptedException {
        String ticker = "TELSA";
        // 首次调用
        BigDecimal firstPrice = gbmPricingStrategy.generatePrice(ticker);
        Map<String, BigDecimal> lastPriceMap = (Map<String, BigDecimal>) ReflectionTestUtils.getField(gbmPricingStrategy, "lastPriceMap");
        Map<String, Long> lastTimestampMap = (Map<String, Long>) ReflectionTestUtils.getField(gbmPricingStrategy, "lastTimestampMap");
        BigDecimal cachedPrice1 = lastPriceMap.get(ticker);
        long cachedTime1 = lastTimestampMap.get(ticker);
        assertEquals(firstPrice, cachedPrice1);

        // 休眠1毫秒确保时间戳变化
        Thread.sleep(1);

        // 第二次调用
        long beforeSecondCall = System.currentTimeMillis();
        BigDecimal secondPrice = gbmPricingStrategy.generatePrice(ticker);
        BigDecimal cachedPrice2 = lastPriceMap.get(ticker);
        long cachedTime2 = lastTimestampMap.get(ticker);

        // 验证缓存更新
        assertEquals(secondPrice, cachedPrice2);
        assertTrue(cachedTime2 >= beforeSecondCall, "时间戳应更新为当前时间");
        assertNotEquals(cachedTime1, cachedTime2, "时间戳应变化");
    }
}