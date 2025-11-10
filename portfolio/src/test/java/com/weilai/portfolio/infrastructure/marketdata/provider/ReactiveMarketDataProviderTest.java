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

    @BeforeEach
    void setUp() {
        // 设置配置参数
        ReflectionTestUtils.setField(marketDataProvider, "minInterval", 500L);
        ReflectionTestUtils.setField(marketDataProvider, "maxInterval", 2000L);
        ReflectionTestUtils.setField(marketDataProvider, "pricingStrategy", "GBM");
    }

    @Test
    void publishMarketData_shouldUseGBMStrategyWhenConfigured() {
        // 给定 - 确保价格已按正确精度格式化
        BigDecimal expectedPrice = BigDecimal.valueOf(100.0)
                .setScale(2, RoundingMode.HALF_UP);
        when(gbmPricingStrategy.generatePrice(anyString())).thenReturn(expectedPrice);

        // 当 & 验证
        Flux<MarketData> marketDataFlux = marketDataProvider.publishMarketData();

        StepVerifier.create(marketDataFlux)
                .expectNextMatches(data ->
                        ("AAPL".equals(data.getTicker()) || "TELSA".equals(data.getTicker())) &&
                                expectedPrice.equals(data.getPrice())
                )
                .expectNextMatches(data ->
                        ("AAPL".equals(data.getTicker()) || "TELSA".equals(data.getTicker())) &&
                                expectedPrice.equals(data.getPrice())
                )
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void publishMarketData_shouldUseRandomStrategyWhenConfigured() {
        // 给定 - 确保价格已按正确精度格式化
        ReflectionTestUtils.setField(marketDataProvider, "pricingStrategy", "RANDOM");
        BigDecimal expectedPrice = BigDecimal.valueOf(150.5)
                .setScale(2, RoundingMode.HALF_UP);
        when(randomPricingStrategy.generatePrice(anyString())).thenReturn(expectedPrice);

        // 当 & 验证
        Flux<MarketData> marketDataFlux = marketDataProvider.publishMarketData();

        StepVerifier.create(marketDataFlux)
                .expectNextMatches(data ->
                        ("AAPL".equals(data.getTicker()) || "TELSA".equals(data.getTicker())) &&
                                expectedPrice.equals(data.getPrice())
                )
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void publishMarketData_shouldHandleMinGreaterThanMaxInterval() {
        // 给定
        ReflectionTestUtils.setField(marketDataProvider, "minInterval", 3000L);
        ReflectionTestUtils.setField(marketDataProvider, "maxInterval", 2000L);
        when(gbmPricingStrategy.generatePrice(anyString())).thenReturn(BigDecimal.valueOf(200.0));

        // 当 & 验证
        Flux<MarketData> marketDataFlux = marketDataProvider.publishMarketData();

        StepVerifier.create(marketDataFlux)
                .expectNextCount(1)
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void publishMarketData_shouldProduceInfiniteStream() {
        // 给定
        when(gbmPricingStrategy.generatePrice(anyString())).thenReturn(BigDecimal.valueOf(100.0));

        // 当 & 验证
        Flux<MarketData> marketDataFlux = marketDataProvider.publishMarketData();

        StepVerifier.create(marketDataFlux)
                .expectNextCount(5)
                .thenCancel()
                .verify(Duration.ofSeconds(10));
    }

    @Test
    void publishMarketData_shouldHandlePricingStrategyErrors() {
        // 给定 - 使用lenient()允许可能的不必要stubbing
        lenient().when(gbmPricingStrategy.generatePrice(anyString()))
                .thenThrow(new RuntimeException("Price generation failed"));

        // 当 & 验证
        Flux<MarketData> marketDataFlux = marketDataProvider.publishMarketData();

        StepVerifier.create(marketDataFlux)
                .expectNextCount(0)
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }
}