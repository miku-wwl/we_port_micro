package com.weilai.portfolio.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 组合估值结果（含单持仓明细 + 总NAV）
 */
@Data
@AllArgsConstructor
public class PortfolioValuation {
    private Position[] positions; // 所有持仓（含实时市值）
    private BigDecimal totalNav; // 组合总净资产
    private long timestamp; // 估值时间戳（毫秒）
    private int updateCount; // 更新序号（#1, #2...）
    private Map<String, BigDecimal> changedMarketData; // 本次更新的市场数据变化
}