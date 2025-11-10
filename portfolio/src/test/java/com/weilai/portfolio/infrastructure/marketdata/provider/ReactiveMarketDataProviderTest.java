package com.weilai.portfolio.infrastructure.marketdata.provider;

import com.weilai.portfolio.entity.MarketData;
import com.weilai.portfolio.infrastructure.marketdata.strategy.GBMPricingStrategy;
import com.weilai.portfolio.infrastructure.marketdata.strategy.RandomPricingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Arrays; // 导入Arrays工具类

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReactiveMarketDataProviderTest {

    @Mock
    private GBMPricingStrategy gbmPricingStrategy;

    @Mock
    private RandomPricingStrategy randomPricingStrategy;

    @InjectMocks
    private ReactiveMarketDataProvider marketDataProvider;

    private static final String[] TEST_TICKERS = {"AAPL", "TELSA", "GOOG"};

    @BeforeEach
    void setUp() {
        // 初始化股票代码数组
        ReflectionTestUtils.setField(marketDataProvider, "STOCK_TICKERS", TEST_TICKERS);
        // 缩短间隔加速测试
        ReflectionTestUtils.setField(marketDataProvider, "minInterval", 100L);
        ReflectionTestUtils.setField(marketDataProvider, "maxInterval", 200L);
        ReflectionTestUtils.setField(marketDataProvider, "pricingStrategy", "GBM");
    }

    @Test
    void publishMarketData_shouldUseGBMStrategyWhenConfigured() {
        BigDecimal expectedPrice = BigDecimal.valueOf(100.0)
                .setScale(2, RoundingMode.HALF_UP);
        when(gbmPricingStrategy.generatePrice(anyString())).thenReturn(expectedPrice);

        Flux<MarketData> marketDataFlux = marketDataProvider.publishMarketData();

        StepVerifier.create(marketDataFlux)
                .expectNextMatches(data ->
                        // JDK 8兼容：数组转List后使用contains
                        Arrays.asList(TEST_TICKERS).contains(data.getTicker()) &&
                                expectedPrice.equals(data.getPrice())
                )
                .expectNextCount(2)
                .thenCancel()
                .verify(Duration.ofSeconds(3));
    }

    @Test
    void publishMarketData_shouldUseRandomStrategyWhenConfigured() {
        ReflectionTestUtils.setField(marketDataProvider, "pricingStrategy", "RANDOM");
        BigDecimal expectedPrice = BigDecimal.valueOf(150.5)
                .setScale(2, RoundingMode.HALF_UP);
        when(randomPricingStrategy.generatePrice(anyString())).thenReturn(expectedPrice);

        Flux<MarketData> marketDataFlux = marketDataProvider.publishMarketData();

        StepVerifier.create(marketDataFlux)
                .expectNextMatches(data ->
                        // 同样使用Arrays.asList转换后判断
                        Arrays.asList(TEST_TICKERS).contains(data.getTicker()) &&
                                expectedPrice.equals(data.getPrice())
                )
                .thenCancel()
                .verify(Duration.ofSeconds(3));
    }

    // 其他测试方法保持不变...
    @Test
    void publishMarketData_shouldHandleMinGreaterThanMaxInterval() {
        ReflectionTestUtils.setField(marketDataProvider, "minInterval", 300L);
        ReflectionTestUtils.setField(marketDataProvider, "maxInterval", 200L);
        when(gbmPricingStrategy.generatePrice(anyString())).thenReturn(BigDecimal.valueOf(200.0));

        Flux<MarketData> marketDataFlux = marketDataProvider.publishMarketData();

        StepVerifier.create(marketDataFlux)
                .expectNextCount(1)
                .thenCancel()
                .verify(Duration.ofSeconds(3));
    }

    @Test
    void publishMarketData_shouldProduceInfiniteStream() {
        when(gbmPricingStrategy.generatePrice(anyString())).thenReturn(BigDecimal.valueOf(100.0));

        Flux<MarketData> marketDataFlux = marketDataProvider.publishMarketData();

        StepVerifier.create(marketDataFlux)
                .expectNextCount(5)
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void publishMarketData_shouldHandlePricingStrategyErrors() {
        lenient().when(gbmPricingStrategy.generatePrice(anyString()))
                .thenThrow(new RuntimeException("Price generation failed"));

        Flux<MarketData> marketDataFlux = marketDataProvider.publishMarketData();

        StepVerifier.create(marketDataFlux)
                .expectNextCount(0)
                .thenCancel()
                .verify(Duration.ofSeconds(3));
    }
}