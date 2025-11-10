package com.weilai.portfolio.service;

import com.weilai.portfolio.entity.MarketData;
import com.weilai.portfolio.entity.PortfolioValuation;
import com.weilai.portfolio.entity.Position;
import com.weilai.portfolio.entity.SecurityType;
import com.weilai.portfolio.grpc.client.PortfolioValuationClient;
import com.weilai.portfolio.grpc.valuation.MarketDataChangeProto;
import com.weilai.portfolio.grpc.valuation.PortfolioValuationMessage;
import com.weilai.portfolio.grpc.valuation.PositionProto;
import com.weilai.portfolio.infrastructure.marketdata.provider.ReactiveMarketDataProvider;
import com.weilai.portfolio.infrastructure.reader.CsvPositionReader;
import com.weilai.portfolio.repository.SecurityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReactivePortfolioValuator {
    private static final Set<String> TARGET_STOCKS = new HashSet<>();

    // 从配置获取接收服务地址
    @Value("${grpc.valuation.server.address:localhost:50052}")
    private String valuationServerAddress;

    @Lazy
    @Autowired
    private PortfolioValuationClient portfolioValuationClient;


    static {
        TARGET_STOCKS.add("AAPL");
        TARGET_STOCKS.add("TELSA");
    }

    private static class PriceState {
        Map<String, MarketData> previousPrices; // 上一次的价格快照
        Map<String, MarketData> currentPrices;  // 当前的价格快照
        int updateCount;

        PriceState(Map<String, MarketData> previousPrices, Map<String, MarketData> currentPrices, int updateCount) {
            this.previousPrices = previousPrices;
            this.currentPrices = currentPrices;
            this.updateCount = updateCount;
        }
    }

    private final SecurityRepository securityRepository;
    private final BlackScholesPricingService optionPricingService;
    private final CsvPositionReader csvPositionReader;
    private final ReactiveMarketDataProvider marketDataProvider;

    public ReactivePortfolioValuator(SecurityRepository securityRepository,
                                     BlackScholesPricingService optionPricingService,
                                     CsvPositionReader csvPositionReader,
                                     ReactiveMarketDataProvider marketDataProvider) {
        this.securityRepository = securityRepository;
        this.optionPricingService = optionPricingService;
        this.csvPositionReader = csvPositionReader;
        this.marketDataProvider = marketDataProvider;
    }

    @Value("${portfolio.option.contract-multiplier}")
    private int contractMultiplier;

    public Flux<PortfolioValuation> calculateRealTimeValuation() {
        Flux<Position> positionFlux = csvPositionReader.readPositions();
        Mono<Position[]> positionsMono = positionFlux
                .collectList()
                .map(list -> list.toArray(new Position[0]))
                .cache();

        Flux<MarketData> marketDataFlux = marketDataProvider.publishMarketData();
        Mono<Map<String, MarketData>> latestPriceCacheMono = Mono.just(new HashMap<>());

        // 生成价格快照流（每500ms一次）
        Flux<HashMap<String, MarketData>> priceSnapshotFlux = marketDataFlux
                .flatMap(marketData -> latestPriceCacheMono.flatMap(cache -> {
                    cache.put(marketData.getTicker(), marketData);
                    return Mono.just(new HashMap<>(cache)); // 返回缓存副本，避免并发修改
                }))
                .sample(Duration.ofMillis(500))
                .filter(cache -> !cache.isEmpty());

        // 调整scan逻辑：记录上一次和当前的价格快照
        Flux<PriceState> priceStateFlux = priceSnapshotFlux
                .scan(
                        // 初始状态：上一次为空，当前为空，计数0
                        new PriceState(new HashMap<>(), new HashMap<>(), 0),
                        // 累加器：新状态的previous = 旧状态的current，新状态的current = 当前快照
                        (previousState, currentSnapshot) ->
                                new PriceState(
                                        previousState.currentPrices,  // 上一次 = 之前的当前
                                        currentSnapshot,              // 当前 = 最新快照
                                        previousState.updateCount + 1 // 计数+1
                                )
                );

        // 生成估值结果（包含价格变化信息）
        return priceStateFlux.flatMap(state -> positionsMono.flatMap(positions -> {
            // 3. 直接从state中获取上一次和当前价格，无需block
            Map<String, BigDecimal> changedPrices = new HashMap<>();
            Map<String, MarketData> currentPrices = state.currentPrices;
            Map<String, MarketData> lastPrices = state.previousPrices; // 这里直接用state保存的上一次价格

            for (String ticker : TARGET_STOCKS) {
                MarketData current = currentPrices.get(ticker);
                MarketData last = lastPrices.get(ticker);
                if (current != null && (last == null || !current.getPrice().equals(last.getPrice()))) {
                    changedPrices.put(ticker, current.getPrice());
                }
            }

            // 计算每个持仓的价值和价格（后续逻辑不变）
            Flux<BigDecimal> positionValueFlux = Flux.fromArray(positions)
                    .flatMap(position -> calculatePositionWithPriceCache(position, currentPrices));

            return positionValueFlux
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .map(totalNav -> {
                        PortfolioValuation valuation = new PortfolioValuation(
                                positions,
                                totalNav,
                                System.currentTimeMillis(),
                                state.updateCount,
                                changedPrices
                        );

                        // 转换为gRPC消息并发送
                        sendValuationToGrpc(valuation);

                        return valuation;
                    });
        }));
    }

    // 转换并发送估值结果
    private void sendValuationToGrpc(PortfolioValuation valuation) {
        log.info("开始发送估值 #{} 到gRPC服务", valuation.getUpdateCount()); // 新增日志
        // 转换Position到PositionProto
        List<PositionProto> positionProtos = Arrays.stream(valuation.getPositions())
                .map(pos -> PositionProto.newBuilder()
                        .setTicker(pos.getTicker())
                        .setQuantity(pos.getQuantity())
                        .setPrice(pos.getPrice().doubleValue())
                        .setMarketValue(pos.getMarketValue().doubleValue())
                        .build())
                .collect(Collectors.toList());

        // 转换市场数据变化
        List<MarketDataChangeProto> marketDataProtos = valuation.getChangedMarketData().entrySet().stream()
                .map(entry -> MarketDataChangeProto.newBuilder()
                        .setTicker(entry.getKey())
                        .setPrice(entry.getValue().doubleValue())
                        .build())
                .collect(Collectors.toList());

        // 构建消息
        PortfolioValuationMessage message = PortfolioValuationMessage.newBuilder()
                .addAllPositions(positionProtos)
                .setTotalNav(valuation.getTotalNav().doubleValue())
                .setTimestamp(valuation.getTimestamp())
                .setUpdateCount(valuation.getUpdateCount())
                .addAllChangedMarketData(marketDataProtos)
                .build();

        // 发送消息
        portfolioValuationClient.sendValuation(
                param -> message,
                response -> log.info("估值消息发送成功: {}", response),
                e -> log.error("估值消息发送失败", e),
                message
        );
    }

    // 计算持仓时设置price字段
    private Mono<BigDecimal> calculatePositionWithPriceCache(Position position, Map<String, MarketData> priceCache) {
        String positionTicker = position.getTicker();
        int quantity = position.getQuantity();

        return securityRepository.findByTicker(positionTicker)
                .flatMap(security -> {
                    MarketData targetMarketData = null;
                    if (security.getSecurityType() == SecurityType.STOCK) {
                        targetMarketData = priceCache.get(positionTicker);
                    } else {
                        String underlyingTicker = security.getUnderlyingTicker();
                        targetMarketData = priceCache.get(underlyingTicker);
                    }

                    if (targetMarketData == null) {
                        log.info("标的{}暂无最新价格，市值暂设为0", positionTicker);
                        position.setPrice(BigDecimal.ZERO);
                        position.setMarketValue(BigDecimal.ZERO);
                        return Mono.just(BigDecimal.ZERO);
                    }

                    // 股票：设置价格为市场价格
                    if (security.getSecurityType() == SecurityType.STOCK) {
                        BigDecimal stockPrice = targetMarketData.getPrice();
                        position.setPrice(stockPrice); // 存储股票当前价格
                        BigDecimal stockValue = stockPrice.multiply(BigDecimal.valueOf(quantity));
                        position.setMarketValue(stockValue);
                        return Mono.just(stockValue);
                    }
                    // 期权：设置价格为理论价格
                    else {
                        double underlyingSigma = getUnderlyingSigma(security.getUnderlyingTicker());
                        BigDecimal optionPrice = optionPricingService.calculate(
                                security.getSecurityType(),
                                targetMarketData.getPrice(),
                                security.getStrikePrice(),
                                calculateTimeToMaturity(security.getMaturityDate()),
                                optionPricingService.getRiskFreeRate(),
                                underlyingSigma
                        );
                        position.setPrice(optionPrice); // 存储期权理论价格
                        BigDecimal optionValue = optionPrice
                                .multiply(BigDecimal.valueOf(quantity))
                                .multiply(BigDecimal.valueOf(contractMultiplier));
                        position.setMarketValue(optionValue);
                        return Mono.just(optionValue);
                    }
                })
                .onErrorResume(error -> {
                    log.error("计算持仓价值异常", error);
                    position.setPrice(BigDecimal.ZERO);
                    position.setMarketValue(BigDecimal.ZERO);
                    return Mono.just(BigDecimal.ZERO);
                });
    }
    // 获取标的股票的σ（与GBM策略中的σ保持一致）
    private double getUnderlyingSigma(String underlyingTicker) {
        // 此处应与GBMPricingStrategy中的σ逻辑一致，可通过配置中心或数据库统一管理
        String ticker = underlyingTicker.toUpperCase();
        double sigma;
        switch (ticker) {
            case "AAPL":
                sigma = 0.2; // 需与GBM的sigmaAAPL保持一致
                break;
            case "TELSA":
                sigma = 0.3; // 需与GBM的sigmaTELSA保持一致
                break;
            default:
                sigma = 0.2;
                break;
        }
        return sigma;
    }

    private double calculateTimeToMaturity(LocalDate maturityDate) {
        if (maturityDate == null) return 0.0001;
        long days = ChronoUnit.DAYS.between(LocalDate.now(), maturityDate);
        return Math.max(0.0001, days / 365.0);
    }
}
