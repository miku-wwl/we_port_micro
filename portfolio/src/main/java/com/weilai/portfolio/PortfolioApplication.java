package com.weilai.portfolio;

import com.weilai.portfolio.grpc.client.PortfolioValuationClient;
import com.weilai.portfolio.service.ReactivePortfolioValuator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@RequiredArgsConstructor
@SpringBootApplication
public class PortfolioApplication implements CommandLineRunner{

    private final ReactivePortfolioValuator valuator;

    @Value("${grpc.valuation.server.address:localhost:50052}")
    private String valuationServerAddress;

    public static void main(String[] args) {
        SpringApplication.run(PortfolioApplication.class, args);
    }

    @Bean
    public PortfolioValuationClient portfolioValuationClient() {
        return new PortfolioValuationClient(valuationServerAddress);
    }

    @Override
    public void run(String... args) throws Exception {
        valuator.calculateRealTimeValuation().subscribe();
    }
}