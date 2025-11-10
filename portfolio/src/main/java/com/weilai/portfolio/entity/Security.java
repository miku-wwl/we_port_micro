package com.weilai.portfolio.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

// 核心实体类（股票/期权的父类，R2DBC可直接映射）
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("security")
public class Security {
    @Id
    private Long id;
    private String ticker; // 标的代码（唯一）
    private SecurityType securityType; // 证券类型
    private BigDecimal strikePrice; // 行权价（仅期权有效）
    private LocalDate maturityDate; // 到期日（仅期权有效）

    // 可选：标的股票代码（如期权 AAPL-OCT-2020-110-C 对应的标的股票是 AAPL）
    private String underlyingTicker;
}