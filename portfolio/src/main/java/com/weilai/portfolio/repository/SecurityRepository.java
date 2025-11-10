package com.weilai.portfolio.repository;

import com.weilai.portfolio.entity.Security;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface SecurityRepository extends R2dbcRepository<Security, Long> {
    // 按标的代码查询证券（返回Mono<Security>，适配响应式）
    Mono<Security> findByTicker(String ticker);
}