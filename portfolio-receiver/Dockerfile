# 构建阶段
FROM openjdk:8-jdk-alpine AS builder

# 配置腾讯云镜像加速（系统依赖）
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.tencent.com/g' /etc/apk/repositories

# 安装必要工具：
# - gcompat：解决Alpine的musl libc与gRPC插件依赖的glibc冲突
# - 其他工具：支持gradle构建和文件处理
RUN apk add --no-cache \
    git \
    bash \
    dos2unix \
    gcompat \
    && rm -rf /var/cache/apk/*

# 设置工作目录
WORKDIR /app

# 先复制gradle相关文件（利用缓存机制，减少重复下载）
COPY gradle/ ./gradle/
COPY gradlew .
COPY build.gradle.kts .
COPY settings.gradle.kts .

# 修复gradlew脚本格式（Windows换行转Linux）和执行权限
RUN dos2unix ./gradlew && chmod +x ./gradlew

# 缓存依赖（指定gRPC插件平台为Linux，避免下载Windows版本）
RUN ./gradlew dependencies --no-daemon \
    -Dprotoc-gen-grpc-java.platform=linux-x86_64 \
    -Dorg.gradle.jvmargs="-Xmx1024m"

# 复制源代码和proto文件（确保gRPC生成代码的源文件被正确复制）
COPY src ./src

# 构建项目（强制使用Linux版本gRPC插件并确保可执行权限）
RUN ./gradlew bootJar -x test --no-daemon \
    -Dmaven.repo.local=/root/.m2/repository \
    -Dorg.gradle.repository.uri=https://mirrors.cloud.tencent.com/nexus/repository/maven-public/ \
    -Dprotoc-gen-grpc-java.platform=linux-x86_64 \
    && find /root/.gradle -name "protoc-gen-grpc-java-1.76.0-linux-x86_64" -exec chmod +x {} \;

# 运行阶段
FROM openjdk:8-jre-alpine

# 配置腾讯云镜像加速
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.tencent.com/g' /etc/apk/repositories

# 安装时区工具（确保日志时间正确）和gcompat（运行时依赖）
RUN apk add --no-cache \
    tzdata \
    gcompat \
    && rm -rf /var/cache/apk/*
ENV TZ=Asia/Shanghai

# 创建非root用户并切换（增强安全性）
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# 设置工作目录
WORKDIR /app

# 复制构建产物（仅复制最终JAR包，减少镜像体积）
COPY --from=builder /app/build/libs/*.jar app.jar

# 暴露端口（对应application.properties中的配置：HTTP端口+gRPC端口）
EXPOSE 8081 50052

# 启动命令（添加JVM参数优化容器环境下的性能和稳定性）
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/app/logs/heapdump.hprof", \
    "-jar", "app.jar"]