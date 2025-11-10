package com.weilai.portfolio.infrastructure.marketdata.strategy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Component
public class GBMPricingStrategy implements PricingStrategy {
    // 每个股票独立的预期收益μ（从配置读取）
    @Value("${portfolio.marketdata.mu.AAPL:0.08}")
    private double muAAPL;
    @Value("${portfolio.marketdata.mu.TELSA:0.12}")
    private double muTELSA;

    // 每个股票独立的波动率σ（从配置读取，覆盖全局波动率）
    @Value("${portfolio.marketdata.sigma.AAPL:0.2}")
    private double sigmaAAPL;
    @Value("${portfolio.marketdata.sigma.TELSA:0.3}")
    private double sigmaTELSA;

    @Value("${portfolio.marketdata.initial-price.AAPL:110.0}")
    private BigDecimal initialPriceAAPL;
    @Value("${portfolio.marketdata.initial-price.TELSA:450.0}")
    private BigDecimal initialPriceTELSA;
    private static final BigDecimal DEFAULT_INIT_PRICE = BigDecimal.valueOf(100.0);

    // 记录每个标的的上一次时间戳（用于计算Δt）
    private final Map<String, Long> lastTimestampMap = new HashMap<>();
    // 保留上一次价格缓存
    private final Map<String, BigDecimal> lastPriceMap = new HashMap<>();

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final double YEAR_SECONDS = 7257600.0; // 附录指定的年秒数
    private final Random random = new Random();

    @Override
    public BigDecimal generatePrice(String ticker) {
        long currentTimestamp = System.currentTimeMillis();
        String upperTicker = ticker.toUpperCase();

        // 首次调用：初始化价格和时间戳
        if (!lastPriceMap.containsKey(upperTicker)) {
            BigDecimal initialPrice;
            switch (upperTicker) {
                case "AAPL":
                    initialPrice = initialPriceAAPL.setScale(SCALE, ROUNDING_MODE);
                    break;
                case "TELSA":
                    initialPrice = initialPriceTELSA.setScale(SCALE, ROUNDING_MODE);
                    break;
                default:
                    initialPrice = DEFAULT_INIT_PRICE;
                    break;
            }
            lastPriceMap.put(upperTicker, initialPrice);
            lastTimestampMap.put(upperTicker, currentTimestamp);
            return initialPrice;
        }

        // 非首次：计算Δt（秒）和新价格
        BigDecimal lastPrice = lastPriceMap.get(upperTicker);
        long lastTimestamp = lastTimestampMap.get(upperTicker);
        double deltaT = (currentTimestamp - lastTimestamp) / 1000.0; // 转换为秒

        // 获取当前标的的μ和σ
        double mu = getMu(upperTicker);
        double sigma = getSigma(upperTicker);

        // 生成标准正态随机变量ε
        double epsilon = random.nextGaussian();

        // 应用附录GBM公式：ΔS/S = μ*(Δt/年秒数) + σ*ε*sqrt(Δt/年秒数)
        double deltaSRatio = mu * (deltaT / YEAR_SECONDS)
                + sigma * epsilon * Math.sqrt(deltaT / YEAR_SECONDS);
        double currentPrice = lastPrice.doubleValue() * (1 + deltaSRatio);

        // 确保价格非负，更新缓存
        BigDecimal finalPrice = BigDecimal.valueOf(Math.max(currentPrice, 0.01))
                .setScale(SCALE, ROUNDING_MODE);
        lastPriceMap.put(upperTicker, finalPrice);
        lastTimestampMap.put(upperTicker, currentTimestamp);

        return finalPrice;
    }

    // 获取标的对应的μ（预期收益）
    private double getMu(String ticker) {
        // 降级后：传统switch语句
        switch (ticker) {
            case "AAPL":
                return muAAPL;
            case "TELSA":
                return muTELSA;
            default:
                return 0.05; // 其他标的默认μ
        }
    }

    // 获取标的对应的σ（波动率）
    private double getSigma(String ticker) {
        switch (ticker) {
            case "AAPL":
                return sigmaAAPL;
            case "TELSA":
                return sigmaTELSA;
            default:
                return 0.2; // 其他标的默认σ
        }
    }
}