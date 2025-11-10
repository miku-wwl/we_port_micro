# 构建阶段
FROM openjdk:8-jdk-alpine AS builder

# 配置腾讯云镜像加速
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.tencent.com/g' /etc/apk/repositories

# 安装必要工具：
# - gcompat：提供glibc兼容层（解决musl与glibc冲突）
# - bash/dos2unix：处理gradlew脚本
# - git：版本控制
RUN apk add --no-cache \
    git \
    bash \
    dos2unix \
    gcompat \
    && rm -rf /var/cache/apk/*

# 设置工作目录
WORKDIR /app

# 先复制gradle相关文件（利用缓存）
COPY gradle/ ./gradle/
COPY gradlew .
COPY build.gradle.kts .
COPY settings.gradle.kts .

# 修复gradlew脚本格式和权限
RUN dos2unix ./gradlew && chmod +x ./gradlew

# 缓存依赖（强制指定gRPC插件的Linux版本）
RUN ./gradlew dependencies --no-daemon \
    -Dorg.gradle.jvmargs="-Xmx1024m" \
    -Dprotoc-gen-grpc-java.platform=linux-x86_64

# 复制源代码
COPY src ./src

# 构建项目（修复gRPC插件权限设置方式）
RUN ./gradlew bootJar -x test --no-daemon \
    -Dmaven.repo.local=/root/.m2/repository \
    -Dorg.gradle.repository.uri=https://mirrors.cloud.tencent.com/nexus/repository/maven-public/ \
    -Dprotoc-gen-grpc-java.platform=linux-x86_64 \
    && find /root/.gradle -name "protoc-gen-grpc-java-1.76.0-linux-x86_64" -exec chmod +x {} \;

# 运行阶段
FROM openjdk:8-jre-alpine

# 配置腾讯云镜像加速
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.tencent.com/g' /etc/apk/repositories
RUN apk add --no-cache tzdata gcompat
ENV TZ=Asia/Shanghai

# 复制构建产物
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

# 暴露端口（根据代码中GrpcServerTemplate默认端口50051调整）
EXPOSE 8080 50051

# 启动命令（添加容器适配参数）
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]