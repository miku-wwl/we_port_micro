package com.weilai.portfolio.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 实时市场数据（股票价格）
 */
@Data
@AllArgsConstructor
public class MarketData {
    private String ticker; // 标的代码（如AAPL）
    private BigDecimal price; // 最新价格
    private long timestamp; // 时间戳（毫秒）
}