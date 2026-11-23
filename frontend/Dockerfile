# ========== 构建阶段 ==========
FROM node:20-alpine AS builder

WORKDIR /build

# 先复制依赖定义文件，充分利用 Docker 层缓存
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# 再复制源码并构建
COPY . .
RUN npm run build

# ========== 运行阶段：Nginx 托管静态文件 + 反代后端 ==========
FROM nginx:1.27-alpine

# 时区
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && apk del tzdata \
    && rm -rf /var/cache/nginx

# 移除默认站点
RUN rm -f /etc/nginx/conf.d/default.conf

# 复制自定义 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 复制构建产物
COPY --from=builder /build/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
