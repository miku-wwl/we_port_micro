package com.weilai.portfolio.infrastructure.reader;

import com.weilai.portfolio.entity.Position;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * CSV 持仓读取器（适配样例 CSV 格式：symbol,positionSize）
 */
@Component
@Slf4j
public class CsvPositionReader {

    // Spring 资源加载器，专门用于读取 classpath 等 Spring 识别的资源
    private final ResourceLoader resourceLoader;
    // CSV 文件路径（从配置文件读取，支持 classpath: 前缀）
    private final String csvFilePath;

    // 构造函数注入 ResourceLoader 和配置的路径（替代字段直接 @Value）
    public CsvPositionReader(ResourceLoader resourceLoader,
                             @Value("${portfolio.position.csv-path}") String csvFilePath) {
        this.resourceLoader = resourceLoader;
        this.csvFilePath = csvFilePath;
    }

    public Flux<Position> readPositions() {
        return Flux.create(sink -> {
            BufferedReader reader = null;
            try {
                // 1. 用 Spring 资源加载器加载文件，支持 classpath: 前缀
                Resource resource = resourceLoader.getResource(csvFilePath);
                // 2. 通过资源对象获取输入流（而非 FileReader）
                reader = new BufferedReader(new InputStreamReader(resource.getInputStream()));

                String line;
                boolean isHeader = true;
                while ((line = reader.readLine()) != null) {
                    // 跳过表头
                    if (isHeader) {
                        isHeader = false;
                        continue;
                    }
                    // 解析 CSV 行
                    String[] parts = line.trim().split(",");
                    if (parts.length != 2) {
                        log.warn("跳过无效 CSV 行：{}", line);
                        continue;
                    }
                    String ticker = parts[0].trim();
                    int quantity = Integer.parseInt(parts[1].trim());
                    // 发送到响应式流
                    sink.next(new Position(ticker, quantity, null, null));
                }
                sink.complete(); // 流处理完成

            } catch (IOException e) {
                log.error("读取 CSV 持仓文件失败，路径：{}", csvFilePath, e);
                sink.error(new RuntimeException("CSV 读取失败", e));
            } catch (NumberFormatException e) {
                log.error("CSV 中 positionSize 不是整数", e);
                sink.error(new RuntimeException("positionSize 格式错误", e));
            } finally {
                // 3. 关闭流，避免资源泄露
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        log.error("关闭 CSV 读取流失败", e);
                    }
                }
            }
        });
    }
}
