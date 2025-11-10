package com.weilai.portfolio.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 持仓实体（适配 CSV 正负多空持仓）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Position {
    private String ticker; // 对应 CSV 的 symbol（支持复合期权代码）
    private int quantity; // 对应 CSV 的 positionSize（支持正负）
    private BigDecimal marketValue; // 实时市值（计算后赋值，可能为负）
    private BigDecimal price;
}