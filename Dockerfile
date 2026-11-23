# ========== 构建阶段 ==========
FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 先复制 pom.xml，利用 Docker 缓存，依赖不变时不用重新下载
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline || true

# 再复制源码并构建
COPY src ./src
RUN mvn -B -q -DskipTests clean package

# ========== 运行阶段 ==========
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 时区
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && apk del tzdata

# 创建非 root 用户运行
RUN addgroup -S app && adduser -S app -G app

# 从构建阶段复制 jar（artifactId 为 booking-queue，version 1.0.0）
COPY --from=builder /build/target/booking-queue-1.0.0.jar /app/app.jar
RUN chown -R app:app /app

USER app

EXPOSE 8080

# G1GC + 合理堆限制
ENTRYPOINT ["java", "-XX:+UseG1GC", "-Xms256m", "-Xmx512m", \
            "-XX:+HeapDumpOnOutOfMemoryError", \
            "-XX:HeapDumpPath=/app/heapdump.hprof", \
            "-jar", "/app/app.jar"]
