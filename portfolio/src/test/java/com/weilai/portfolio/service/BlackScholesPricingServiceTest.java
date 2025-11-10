package com.weilai.portfolio.service;

import com.weilai.portfolio.entity.SecurityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class BlackScholesPricingServiceTest {

    @InjectMocks
    private BlackScholesPricingService pricingService;

    // 测试用例中常用的参数
    private static final BigDecimal UNDERLYING_PRICE = BigDecimal.valueOf(100);
    private static final BigDecimal STRIKE_PRICE = BigDecimal.valueOf(100);
    private static final double TIME_TO_MATURITY = 1.0; // 1年
    private static final double RISK_FREE_RATE = 0.02;
    private static final double VOLATILITY = 0.2;

    @BeforeEach
    void setUp() {
        // 初始化配置参数（通过反射设置私有字段）
        ReflectionTestUtils.setField(pricingService, "riskFreeRate", 0.02);
        ReflectionTestUtils.setField(pricingService, "volatility", 0.2);
    }

    /**
     * 测试参数校验：当基础资产价格<=0时返回0
     */
    @Test
    void calculate_WithUnderlyingPriceZero_ReturnsZero() {
        BigDecimal result = pricingService.calculate(
                SecurityType.CALL,
                BigDecimal.ZERO,
                STRIKE_PRICE,
                TIME_TO_MATURITY,
                RISK_FREE_RATE,
                VOLATILITY
        );
        assertEquals(BigDecimal.ZERO, result);
    }

    /**
     * 测试参数校验：当行权价<=0时返回0
     */
    @Test
    void calculate_WithStrikePriceZero_ReturnsZero() {
        BigDecimal result = pricingService.calculate(
                SecurityType.PUT,
                UNDERLYING_PRICE,
                BigDecimal.ZERO,
                TIME_TO_MATURITY,
                RISK_FREE_RATE,
                VOLATILITY
        );
        assertEquals(BigDecimal.ZERO, result);
    }

    /**
     * 测试参数校验：当到期时间<=0时返回0
     */
    @Test
    void calculate_WithTimeToMaturityZero_ReturnsZero() {
        BigDecimal result = pricingService.calculate(
                SecurityType.CALL,
                UNDERLYING_PRICE,
                STRIKE_PRICE,
                0.0,
                RISK_FREE_RATE,
                VOLATILITY
        );
        assertEquals(BigDecimal.ZERO, result);
    }

    /**
     * 测试看涨期权价格计算（平值期权，已知理论值约为8.90）
     */
    @Test
    void calculate_CallOption_ValidResult() {
        BigDecimal result = pricingService.calculate(
                SecurityType.CALL,
                UNDERLYING_PRICE, // S=100
                STRIKE_PRICE,    // K=100
                TIME_TO_MATURITY, // T=1
                RISK_FREE_RATE,   // r=0.02
                VOLATILITY        // σ=0.2
        );

        // 预期值约为8.90（允许小误差）
        assertTrue(result.compareTo(BigDecimal.valueOf(8.80)) > 0);
        assertTrue(result.compareTo(BigDecimal.valueOf(9.00)) < 0);
    }

    /**
     * 测试看跌期权价格计算（平值期权，已知理论值约为7.02）
     */
    @Test
    void calculate_PutOption_ValidResult() {
        BigDecimal result = pricingService.calculate(
                SecurityType.PUT,
                UNDERLYING_PRICE, // S=100
                STRIKE_PRICE,    // K=100
                TIME_TO_MATURITY, // T=1
                RISK_FREE_RATE,   // r=0.02
                VOLATILITY        // σ=0.2
        );

        // 预期值约为7.02（允许小误差）
        assertTrue(result.compareTo(BigDecimal.valueOf(6.90)) > 0);
        assertTrue(result.compareTo(BigDecimal.valueOf(7.10)) < 0);
    }

    /**
     * 测试实值看涨期权（S > K）
     */
    @Test
    void calculate_InTheMoneyCallOption() {
        BigDecimal result = pricingService.calculate(
                SecurityType.CALL,
                BigDecimal.valueOf(120), // S=120
                BigDecimal.valueOf(100), // K=100
                0.5,                     // T=0.5年
                0.03,                    // r=3%
                0.25                     // σ=25%
        );

        // 实值看涨期权价格应较高（约22.5左右）
        assertTrue(result.compareTo(BigDecimal.valueOf(22.0)) > 0);
        assertTrue(result.compareTo(BigDecimal.valueOf(23.0)) < 0);
    }

    /**
     * 测试虚值看跌期权（S > K）
     */
    @Test
    void calculate_OutOfTheMoneyPutOption() {
        BigDecimal result = pricingService.calculate(
                SecurityType.PUT,
                BigDecimal.valueOf(120), // S=120
                BigDecimal.valueOf(100), // K=100
                0.5,                     // T=0.5年
                0.03,                    // r=3%
                0.25                     // σ=25%
        );

        // 虚值看跌期权价格应较低（约1.5左右）
        assertTrue(result.compareTo(BigDecimal.valueOf(1.0)) > 0);
        assertTrue(result.compareTo(BigDecimal.valueOf(2.0)) < 0);
    }

    /**
     * 测试正态分布累积分布函数（CDF）
     * 标准正态分布中：
     * - CDF(0) ≈ 0.5
     * - CDF(1) ≈ 0.8413
     * - CDF(-1) ≈ 0.1587
     */
    @Test
    void normalCdf_StandardValues() {
        // 通过反射调用私有方法
        double cdfZero = (double) ReflectionTestUtils.invokeMethod(pricingService, "normalCdf", 0.0);
        double cdfOne = (double) ReflectionTestUtils.invokeMethod(pricingService, "normalCdf", 1.0);
        double cdfNegOne = (double) ReflectionTestUtils.invokeMethod(pricingService, "normalCdf", -1.0);

        assertEquals(0.5, cdfZero, 0.001);
        assertEquals(0.8413, cdfOne, 0.001);
        assertEquals(0.1587, cdfNegOne, 0.001);
    }

    /**
     * 测试d1计算逻辑
     * 修正：根据Black-Scholes公式，正确计算结果应为0.2而非0.1
     */
    @Test
    void calculateD1_ValidValue() {
        // 调用私有方法
        double d1 = (double) ReflectionTestUtils.invokeMethod(
                pricingService,
                "calculateD1",
                100.0,  // S
                100.0,  // K
                1.0,    // T
                0.02,   // r
                0.2     // σ
        );

        // 根据公式计算：d1 = [ln(S/K) + (r + 0.5σ²)T] / (σ√T)
        // 代入值：[0 + (0.02 + 0.5*0.04)*1]/(0.2*1) = 0.04/0.2 = 0.2
        assertEquals(0.2, d1, 0.001);
    }

    /**
     * 测试期权价格为负时的修正（应返回0）
     * 修复：使用compareTo而非equals比较BigDecimal
     */
    @Test
    void calculate_NegativePrice_ReturnsZero() {
        // 构造极端参数使计算结果为负（短期虚值期权可能出现）
        BigDecimal result = pricingService.calculate(
                SecurityType.PUT,
                BigDecimal.valueOf(200), // 远高于行权价
                BigDecimal.valueOf(100),
                0.001, // 极短到期时间
                0.1,   // 高利率
                0.1    // 低波动率
        );

        // 使用compareTo比较BigDecimal，避免精度问题导致的比较失败
        assertEquals(0, result.compareTo(BigDecimal.ZERO));
    }
}