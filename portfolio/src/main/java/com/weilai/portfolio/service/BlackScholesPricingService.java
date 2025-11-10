package com.weilai.portfolio.service;

import com.weilai.portfolio.entity.SecurityType;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class BlackScholesPricingService {
    @Value("${portfolio.option.risk-free-rate:0.02}")
    @Getter
    private double riskFreeRate;

    @Value("${portfolio.option.volatility:0.2}")
    @Getter
    private double volatility;

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    public BigDecimal calculate(
            SecurityType securityType,
            BigDecimal underlyingPrice,
            BigDecimal strikePrice,
            double timeToMaturity,
            double riskFreeRate,
            double volatility
    ) {
        if (underlyingPrice.compareTo(BigDecimal.ZERO) <= 0
                || strikePrice.compareTo(BigDecimal.ZERO) <= 0
                || timeToMaturity <= 0) {
            return BigDecimal.ZERO;
        }

        double S = underlyingPrice.doubleValue();
        double K = strikePrice.doubleValue();
        double T = timeToMaturity;
        double r = riskFreeRate;
        double sigma = volatility;

        double d1 = calculateD1(S, K, T, r, sigma);
        double d2 = d1 - sigma * Math.sqrt(T);

        double nd1 = normalCdf(d1);
        double nd2 = normalCdf(d2);

        BigDecimal optionPrice;
        if (securityType == SecurityType.CALL) {
            double callPrice = S * nd1 - K * Math.exp(-r * T) * nd2;
            optionPrice = BigDecimal.valueOf(callPrice).setScale(SCALE, ROUNDING_MODE);
        } else {
            double putPrice = K * Math.exp(-r * T) * normalCdf(-d2) - S * normalCdf(-d1);
            optionPrice = BigDecimal.valueOf(putPrice).setScale(SCALE, ROUNDING_MODE);
        }

        return optionPrice.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : optionPrice;
    }

    private double calculateD1(double S, double K, double T, double r, double sigma) {
        double numerator = Math.log(S / K) + (r + 0.5 * sigma * sigma) * T;
        double denominator = sigma * Math.sqrt(T);
        return denominator == 0 ? 0 : numerator / denominator;
    }

    private double normalCdf(double x) {
        boolean isNegative = x < 0;
        double absX = Math.abs(x);

        double t = 1.0 / (1.0 + 0.2316419 * absX);
        double t2 = t * t;
        double t3 = t2 * t;
        double t4 = t3 * t;
        double t5 = t4 * t;

        double polynomial = 0.319381530 * t
                - 0.356563782 * t2
                + 1.781477937 * t3
                - 1.821255978 * t4
                + 1.330274429 * t5;

        double phi = Math.exp(-0.5 * absX * absX) / Math.sqrt(2 * Math.PI);
        double cdf = 1.0 - phi * polynomial;

        return isNegative ? 1.0 - cdf : cdf;
    }
}