# 多门店预约排队系统

> Spring Boot 3 + Vue3 的校招简历项目：单机可起、Docker 一键起、200 并发压测过。

## 架构总览

```
┌──────────────────────────────┐     ┌──────────────────────────────┐
│     顾客端 H5 (Vue3 + Vite)  │     │   管理后台 (Vue3 + Element)  │
│     mobile-adaptive          │     │   侧边栏 + 四个核心页面       │
└───────────────┬──────────────┘     └───────────────┬──────────────┘
                │                                     │
                └───────────────┬─────────────────────┘
                                │  HTTP
                       ┌────────▼────────┐
                       │   Nginx (80)    │ ← SPA 托管 + /api/ 反代
                       └────────┬────────┘
                                │
┌───────────────────────────────┼───────────────────────────────┐
│                         Spring Boot 3 后端                      │
│                                                                 │
│  ┌─ Controller 层 ──────────────────────────────────────────┐  │
│  │ AppointmentController   PayController                      │  │
│  │ QueueController        AdminController                    │  │
│  │ StoreController        AuthController                      │  │
│  └──────────────────────┬───────────────────────────────────┘  │
│                         │                                       │
│  ┌─ Service 层（状态机 / 事务边界）───────────────────────────┐  │
│  │ AppointmentService      create / cancel / verify / noShow  │  │
│  │ PayCallbackService      handle / mockPay                   │  │
│  │ OrderTimeoutService     handleTimeout（延时关单 + 补投）   │  │
│  │ QueueTicketService      take / call / finish              │  │
│  │ StoreQueryService       门店 + 时段（Redis 缓存）          │  │
│  │ AuthService             login / current                    │  │
│  └──────────────────────┬───────────────────────────────────┘  │
│                         │                                       │
│  ┌─ Mapper 层 (MyBatis-Plus) ───────────────────────────────┐  │
│  │ BookingOrderMapper    PaymentRecordMapper                  │  │
│  │ TimeSlotStockMapper   QueueTicketMapper                    │  │
│  │ SysUserMapper         StoreMapper                          │  │
│  └──────────────────────┬───────────────────────────────────┘  │
│                         │                                       │
│  ┌─ 支撑模块 ────────────────────────────────────────────────┐  │
│  │ Spring Security + JWT   Redis Cache AOP                    │  │
│  │ RocketMQ 延时消息        事件 + AFTER_COMMIT 监听器         │  │
│  │ 全局异常 / TraceId      统一 Result / 参数校验             │  │
│  └──────────────────────┬───────────────────────────────────┘  │
└─────────────────────────┼─────────────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        │                 │                 │
   ┌────▼────┐      ┌─────▼────┐      ┌─────▼─────┐
   │ MySQL 8 │      │ Redis 7  │      │ RocketMQ 5│
   │ (utf8mb4)│     │ (缓存)   │      │ (延时消息) │
   └─────────┘      └──────────┘      └───────────┘
```

**关键设计**

| 能力 | 实现 | 面试可问 |
|---|---|---|
| 并发扣库存 | `UPDATE ... WHERE remain_stock > 0` 条件更新，无 SELECT FOR UPDATE | 单机热点上限？ |
| 幂等下单 | `Idempotency-Key` 先查后插 + `uk_user_idempotent` 唯一索引兜底 | 为什么两层？ |
| 订单状态机 | `PENDING_PAY → PAID → VERIFIED / NO_SHOW / CANCELED / TIMEOUT_CANCELED`，条件更新自带 CAS | 怎么防并发非法流转？ |
| 超时关单 | RocketMQ 15min 延时消息（多档补投）+ 每分钟定时扫表兜底 | 为什么双保险？ |
| 过号处理 | RocketMQ 3min 延时 + 定时扫表兜底 | 与超时关单同一套 |
| 支付回调幂等 | `trade_no` 唯一索引 + 订单状态机条件更新 两层 | 重复回调怎么不重复发通知？ |
| JWT 鉴权 | HS256，claims: userId/phone/nickname/role/storeId，120min | 怎么续签/失效？ |
| 排队取号 | Redis INCR 按门店+服务+当日分桶，DB 唯一索引兜底 | 单机/分布式？ |

## 技术栈

| 分类 | 技术 | 版本 |
|---|---|---|
| 后端 | Spring Boot | 3.2.11 |
| | MyBatis-Plus | 3.5.7 |
| | Spring Security + JWT (jjwt) | 12.6 |
| | MySQL | 8.0 |
| | Redis | 7 |
| | RocketMQ | 5.3 |
| | Knife4j (OpenAPI) | 4.5 |
| 前端 | Vue 3 + Vite | 5.4 |
| | Element Plus | 2.7 |
| 运行 | Docker / Docker Compose | v2 |
| 开发环境 | Windows + JDK 17 + Maven 3.8 + Node 20 | |

## ER 关系（12 张表）

```
store ──1:N──▶ service_item ──N:M──▶ resource_info    (service_resource)
store ──1:N──▶ resource_info
resource_info ──1:N──▶ schedule           (排班)
schedule ──1:N──▶ time_slot_stock         (库存可售名额)
time_slot_stock ──1:N──▶ booking_order    (预约单)
booking_order ──1:N──▶ payment_record      (支付流水)
booking_order ──1:1──▶ queue_ticket        (排队号，或取号现场生成)
sys_user ──1:N──▶ booking_order / payment_record / queue_ticket / notification
```

每张表的核心字段、索引和设计说明在 `src/main/resources/db/schema.sql` 顶部注释里。

## 目录结构

```
project_s/
├── src/main/java/com/example/booking/
│   ├── controller/        # 6 个 REST 控制器
│   ├── service/           # 7 个业务服务（含状态机）
│   ├── mapper/            # MyBatis-Plus 接口
│   ├── domain/            # 实体 + 枚举（OrderStatus / TicketStatus / UserRole）
│   ├── dto/               # 请求/响应 VO
│   ├── config/            # Security / JWT / RedisCache / MybatisPlus
│   ├── common/            # Result / BizException / TraceIdFilter / CacheEvictSupport
│   ├── security/          # JwtTokenProvider / JwtAuthenticationFilter
│   ├── mq/                # RocketMQ 发送 + 消费（延时关单、过号）
│   ├── event/             # Spring 领域事件
│   └── task/              # 定时扫表兜底（OrderTimeoutCompensateTask）
├── src/main/resources/
│   ├── application-local.yml
│   ├── application-docker.yml
│   └── db/
│       ├── schema.sql     # 建表脚本（12 张表）
│       └── seed.sql       # 演示/压测种子数据
├── frontend/              # Vue3 前端（开发态 Vite / 生产态 Nginx）
├── scripts/
│   └── load_test_booking.py   # 并发 + 幂等压测脚本（纯 Python 标准库）
├── docker-compose.yml     # MySQL + Redis + RocketMQ + 后端 + 前端 Nginx 一键起
├── Dockerfile             # 后端多阶段构建
└── docker/rocketmq/broker.conf
```

## 一键启动（推荐）

Docker Compose 已把 MySQL 初始化（schema + seed）、Redis、RocketMQ、后端（Spring Boot docker profile）、前端（Nginx 反代）串起来，一条命令全起来：

```bash
docker compose up -d
```

容器启动顺序（compose 自动处理健康检查依赖）：

```
mysql → redis → rocketmq-namesrv → rocketmq-broker → backend → frontend
```

端口映射：

| 服务 | 容器内 | 宿主 |
|---|---|---|
| 前端 Nginx | 80 | http://localhost:80 |
| 后端 Spring Boot | 8080 | http://localhost:8080 |
| Knife4j 接口文档 | — | http://localhost:8080/doc.html |
| MySQL | 3306 | localhost:3306 |
| Redis | 6379 | localhost:6379 |
| RocketMQ NameServer | 9876 | localhost:9876 |

一键停：`docker compose down`；清数据卷重建：`docker compose down -v && docker compose up -d`。

## 本地开发启动（不走 Docker）

前提：本地 MySQL（端口 3306，root/123456）+ Redis（6379）+ 可选 RocketMQ（本地 `consumer-enabled: false`）。

```bash
# 1. 建表 + 种子数据（注意 --default-character-set=utf8mb4 避免中文乱码）
mysql -uroot -p123456 --default-character-set=utf8mb4 \
  < src/main/resources/db/schema.sql
mysql -uroot -p123456 --default-character-set=utf8mb4 \
  < src/main/resources/db/seed.sql

# 2. 起后端（local profile，端口 8080）
mvn -q -DskipTests spring-boot:run

# 3. 起前端（开发态 Vite，默认 http://127.0.0.1:5173）
cd frontend && npm run dev
```

## 演示账号（密码均为 `123456`）

| 手机号 | 角色 | store_id | 说明 |
|---|---|---|---|
| `13900000000` | 店长 (role=2) | 1 | 管理端默认入口 |
| `13900000001` | 店员 (role=1) | 1 | 管理端也能进 |
| `13800001000` | 顾客 (role=0) | — | H5 顾客演示账号 |
| `13800000002` | 顾客 (role=0) | — | 备用 |
| `13800001001` ~ `13800001200` | 顾客 | — | 压测账号（200 个） |

后端接口鉴权：除 `/api/auth/login`、`/api/stores/**`、`/api/pay/callback`、文档外，其余接口都要 JWT。

## 关键 API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 手机号 + 密码登录 |
| GET | `/api/auth/me` | 当前登录用户 |
| GET | `/api/stores` | 门店列表（公开） |
| GET | `/api/stores/{id}/slots?serviceId=&date=` | 时段库存（Redis 缓存） |
| POST | `/api/appointments` | 下单，Header `Idempotency-Key` |
| POST | `/api/appointments/{id}/cancel` | 取消预约（仅待支付） |
| GET | `/api/appointments/mine` | 我的预约（JWT 身份） |
| POST | `/api/pay/mock` | 模拟支付（复用真实回调链路） |
| POST | `/api/pay/callback` | 支付回调入口（验签 + 幂等） |
| POST | `/api/queue/tickets` | 排队取号（Redis INCR + DB 兜底） |
| GET | `/api/admin/**` | 管理端，需 `ROLE_STAFF` 或 `ROLE_ADMIN` |

## 并发压测结果

压测脚本：`scripts/load_test_booking.py`（纯 Python 标准库）。前置：执行过 `seed.sql`（含 200 压测账号 + 未来时段）。

### 测试一：200 并发抢 5 名额

```
== 并发抢名额：200 线程抢 5 个名额 ==
  登录 200 个种子账号 ...
  请求数=200  HTTP200=200
  code 分布={40002: 195, 0: 5}
  耗时(ms) P50=161.0 P95=214.5 P99=220.0 max=226.3 avg=158.5
  [PASS] 全部 HTTP 200
  [PASS] 成功数 = 库存数（5/5）
  [PASS] 售罄数 = 195
  [PASS] 订单号不重复
```

### 测试二：50 线程幂等下单

```
== 幂等验证：50 线程使用同一个 Idempotency-Key ==
  请求数=50  HTTP200=50
  code 分布={0: 50}
  耗时(ms) P50=141.7 P95=183.1 P99=185.6 max=185.6
  [PASS] 全部 HTTP 200
  [PASS] 全部返回成功（50/50）
  [PASS] 只产生一个订单号
  [PASS] 库存只扣 1（未跑，可 --check-db 开启）
```

命令：

```bash
# 重置目标时段（slot_id 先查询）
python scripts/load_test_booking.py concurrency --slot-id <slot_id> --check-db --mysql-password 123456
python scripts/load_test_booking.py idempotent  --slot-id <slot_id>
```

## 常见面试追问（附简要要点）

1. **为什么取消扣库存用条件更新，不用 SELECT FOR UPDATE？**
   要点：条件更新把「判断」和「自减」合成一条 SQL，InnoDB 命中行后直接加 X 锁，后到事务等前一事务提交再判断——一次往返就够了。SELECT FOR UPDATE 要先 SELECT（S 锁）再 UPDATE（升级到 X 锁），锁持有时间更长、死锁概率更高。单机单行热点，千级 QPS 能扛住。

2. **JWT 怎么主动失效？**
   要点：纯无状态 JWT 登出只删前端 token，服务端看不到。做法：短过期 + Refresh Token，或 Redis 维护 jti 黑名单/用户 token 版本号（每次改密/注销自增，校验时比对）。本项目 demo 用 120 分钟固定过期，面试讲清取舍即可。

3. **支付回调幂等怎么防重复发通知、重复加积分？**
   要点：双层——`trade_no` 唯一索引防回调本身重复；订单状态机条件更新（`WHERE status = PENDING_PAY`）防"换了 trade_no 的重试"把已支付订单再推一次。通知/积分都挂在状态变更之后，状态没推进=后续逻辑都不执行。

4. **为什么超时关单要 RocketMQ + 定时扫表双保险？**
   要点：MQ 丢消息、消费者挂了、RocketMQ 自身故障都可能导致关单没执行。定时扫表兜底 `WHERE status = PENDING_PAY AND pay_deadline < NOW()`，一分钟扫一次，作为 MQ 的补充。关键业务不能只信一个通道。

## 后续扩展（简历 + 1）

- 退款流程：`PAID → REFUNDING → REFUNDED`，复用状态机模式
- 通知落库 + 站内信（MQ 延时消息 → notification 表）
- 管理端排班直接维护 `time_slot_stock`（不建独立 schedule 表）
- Redis Cache Aside + 穿透/击穿/雪崩保护（已实现）
