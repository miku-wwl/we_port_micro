package com.weilai.portfolio;

import com.weilai.portfolio.grpc.server.GrpcServerTemplate;
import com.weilai.portfolio.grpc.server.PortfolioValuationServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;

@SpringBootApplication
public class PortfolioReceiverApplication implements CommandLineRunner {

    @Value("${grpc.server.port:50052}")
    private int grpcPort;

    private final com.weilai.portfolio.infrastructure.subscriber.PortfolioValuationSubscriber valuationSubscriber;

    public PortfolioReceiverApplication(com.weilai.portfolio.infrastructure.subscriber.PortfolioValuationSubscriber valuationSubscriber) {
        this.valuationSubscriber = valuationSubscriber;
    }

    public static void main(String[] args) {
        SpringApplication.run(PortfolioReceiverApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        startGrpcServer();
    }

    private void startGrpcServer() throws IOException, InterruptedException {
        PortfolioValuationServer valuationServer = new PortfolioValuationServer(valuationSubscriber);
        GrpcServerTemplate serverWrapper = new GrpcServerTemplate(grpcPort, valuationServer);
        serverWrapper.start();
        serverWrapper.blockUntilShutdown();
    }
}