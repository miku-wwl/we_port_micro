package com.weilai.portfolio.grpc.server;

import com.weilai.portfolio.entity.PortfolioValuation;
import com.weilai.portfolio.entity.Position;
import com.weilai.portfolio.grpc.valuation.PortfolioValuationMessage;
import com.weilai.portfolio.grpc.valuation.PortfolioValuationServiceGrpc;
import com.weilai.portfolio.grpc.valuation.ValuationResponse;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class PortfolioValuationServer extends PortfolioValuationServiceGrpc.PortfolioValuationServiceImplBase {

    private final com.weilai.portfolio.infrastructure.subscriber.PortfolioValuationSubscriber valuationSubscriber;
    private int receivedCount = 0;

    @Override
    public void sendValuation(PortfolioValuationMessage request,
                              StreamObserver<ValuationResponse> responseObserver) {
        try {
            // 将gRPC消息转换为本地实体
            PortfolioValuation valuation = convertToPortfolioValuation(request);
            receivedCount++;

            // 处理估值结果
            valuationSubscriber.handleValuation(valuation);

            // 发送响应
            ValuationResponse response = ValuationResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("估值已接收，累计接收: " + receivedCount)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("接收估值更新 #{}", request.getUpdateCount());
        } catch (Exception e) {
            log.error("处理估值消息失败", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public StreamObserver<PortfolioValuationMessage> streamValuations(
            StreamObserver<ValuationResponse> responseObserver) {
        return new StreamObserver<PortfolioValuationMessage>() {
            @Override
            public void onNext(PortfolioValuationMessage request) {
                try {
                    PortfolioValuation valuation = convertToPortfolioValuation(request);
                    receivedCount++;
                    valuationSubscriber.handleValuation(valuation);
                    log.info("流式接收估值更新 #{}，累计: {}", request.getUpdateCount(), receivedCount);
                } catch (Exception e) {
                    log.error("处理流式估值消息失败", e);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("流式处理错误", t);
            }

            @Override
            public void onCompleted() {
                ValuationResponse response = ValuationResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage("流式估值接收完成，共接收: " + receivedCount)
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        };
    }

    // 转换gRPC消息到本地实体
    private PortfolioValuation convertToPortfolioValuation(PortfolioValuationMessage request) {
        Position[] positions = request.getPositionsList().stream()
                .map(posProto -> {
                    Position position = new Position();
                    position.setTicker(posProto.getTicker());
                    position.setQuantity(posProto.getQuantity());
                    position.setPrice(BigDecimal.valueOf(posProto.getPrice()));
                    position.setMarketValue(BigDecimal.valueOf(posProto.getMarketValue()));
                    return position;
                })
                .toArray(Position[]::new);

        Map<String, BigDecimal> changedMarketData = new HashMap<>();
        request.getChangedMarketDataList().forEach(change ->
                changedMarketData.put(change.getTicker(), BigDecimal.valueOf(change.getPrice())));

        return new PortfolioValuation(
                positions,
                BigDecimal.valueOf(request.getTotalNav()),
                request.getTimestamp(),
                request.getUpdateCount(),
                changedMarketData
        );
    }
}