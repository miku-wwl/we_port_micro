package com.weilai.portfolio.infrastructure.marketdata.strategy;

import java.math.BigDecimal;

/**
 * 市场定价策略接口（策略模式）
 */
public interface PricingStrategy {
    /**
     * 生成标的实时价格
     * @param ticker 标的代码
     * @return 实时价格
     */
    BigDecimal generatePrice(String ticker);
}