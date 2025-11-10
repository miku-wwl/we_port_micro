package com.weilai.portfolio.infrastructure.subscriber;

import com.weilai.portfolio.entity.PortfolioValuation;
import com.weilai.portfolio.entity.Position;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 投资组合估值订阅者（统一类名，适配启动类注入）
 */
@Component
@RequiredArgsConstructor
public class PortfolioValuationSubscriber {
    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,##0.00 USD");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public void subscribe(Flux<PortfolioValuation> valuationFlux) {
        Disposable disposable = valuationFlux
                // 过滤掉总净资产为0的估值结果
                .filter(valuation -> valuation.getTotalNav().compareTo(BigDecimal.ZERO) > 0)
                .subscribe(this::printValuationResult, this::handleError);

        // 程序关闭时取消订阅，避免资源泄露
        Runtime.getRuntime().addShutdownHook(new Thread(disposable::dispose));
    }
    private void printValuationResult(PortfolioValuation valuation) {
        // 1. 打印市场数据更新
        System.out.println("# " + valuation.getUpdateCount() + " Market Data Update");
        Map<String, BigDecimal> changedPrices = valuation.getChangedMarketData();
        for (Map.Entry<String, BigDecimal> entry : changedPrices.entrySet()) {
            System.out.printf("%s change to %s%n", entry.getKey(), PRICE_FORMAT.format(entry.getValue()));
        }

        // 2. 打印估值时间和总净资产（保留原有格式）
        System.out.println();
        LocalDateTime valuationTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(valuation.getTimestamp()),
                ZoneId.systemDefault()
        );
        System.out.printf("[Valuation Time]: %s%n", valuationTime.format(TIME_FORMAT));
        System.out.printf("[Portfolio Total Net Asset Value (NAV)]: %s%n", CURRENCY_FORMAT.format(valuation.getTotalNav()));
        // 3. 打印持仓表格
        System.out.println("# Portfolio");
        // 表头（左对齐25字符，右对齐10字符，右对齐10字符，右对齐15字符）
        System.out.printf("%-25s %10s %10s %15s%n", "symbol", "price", "qty", "value");
        for (Position position : valuation.getPositions()) {
            System.out.printf(
                    "%-25s %10s %10d %15s%n",
                    position.getTicker(),
                    PRICE_FORMAT.format(position.getPrice()),
                    position.getQuantity(),
                    CURRENCY_FORMAT.format(position.getMarketValue())
            );
        }

        // 4. 打印组合总价值
        System.out.println("# Total portfolio");
        System.out.println(PRICE_FORMAT.format(valuation.getTotalNav()) + " USD");
        System.out.println("======================================================");
        System.out.println(); // 空行分隔不同更新
    }

    private void handleError(Throwable error) {
        System.err.println("======================================================");
        System.err.println("估值异常：" + error.getMessage());
        error.printStackTrace();
        System.err.println("======================================================");
        System.err.println();
    }

    public void handleValuation(PortfolioValuation valuation) {
        // 过滤掉总净资产为0的估值结果
        if (valuation.getTotalNav().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        printValuationResult(valuation);
    }
}