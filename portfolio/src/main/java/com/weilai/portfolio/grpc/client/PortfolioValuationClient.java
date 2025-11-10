package com.weilai.portfolio.grpc.client;

import com.weilai.portfolio.grpc.valuation.PortfolioValuationMessage;
import com.weilai.portfolio.grpc.valuation.PortfolioValuationServiceGrpc;
import com.weilai.portfolio.grpc.valuation.ValuationResponse;
import io.grpc.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
public class PortfolioValuationClient {
    private final GrpcClientTemplate<PortfolioValuationMessage, ValuationResponse> valuationClient;

    public PortfolioValuationClient(String target) {
        this.valuationClient = new GrpcClientTemplate<PortfolioValuationMessage, ValuationResponse>(target) {
            @Override
            protected Object createBlockingStub(Channel channel) {
                return PortfolioValuationServiceGrpc.newBlockingStub(channel);
            }

            @Override
            protected ValuationResponse doRpcCall(Object stub, PortfolioValuationMessage request) {
                PortfolioValuationServiceGrpc.PortfolioValuationServiceBlockingStub valuationStub =
                        (PortfolioValuationServiceGrpc.PortfolioValuationServiceBlockingStub) stub;
                return valuationStub.sendValuation(request);
            }
        };
    }

    public <T> void sendValuation(Function<T, PortfolioValuationMessage> requestBuilder,
                                  Consumer<ValuationResponse> responseHandler,
                                  T businessParam) {
        valuationClient.execute(requestBuilder, responseHandler, businessParam);
    }

    public <T> void sendValuation(Function<T, PortfolioValuationMessage> requestBuilder,
                                  Consumer<ValuationResponse> responseHandler,
                                  Consumer<Throwable> exceptionHandler,
                                  T businessParam) {
        valuationClient.execute(requestBuilder, responseHandler, exceptionHandler, businessParam);
    }
}