# 多门店预约排队系统 · 面试全景指南

> 这份文档是给**你自己看的**——帮助理解项目关键设计、面试时能快速回忆、应对追问。
> 仓库地址：https://github.com/qinghe2233/line

---

## 0. 一句话开场白（简历/自我介绍用）

> 基于 Spring Boot 3 + Vue3 做的多门店预约排队系统，核心解决**单机下高并发扣库存的一致性问题**（条件更新 + 幂等键），搭配**状态机**保证订单状态只合法流转，用 RocketMQ 延时消息 + 定时扫表做超时关单/过号双保险，单机 200 并发压测能稳定挡住。

**技术栈 2 行版**：

```
后端：Spring Boot 3.2.11 / MyBatis-Plus 3.5.7 / Spring Security + JWT (jjwt 0.12) /
      MySQL 8 / Redis 7 / RocketMQ 5.3 / Knife4j / Docker Compose
前端：Vue 3 + Vite 5 + Element Plus 2.7（管理端侧边栏 + 顾客端 H5 移动端适配）
部署：docker compose up -d 一键起（MySQL 自动初始化 schema + seed）
```

**10 次 commit 演进**（git log 能看到完整轨迹）：

```
init → db schema + enums → seed + entities + mappers → JWT auth → store/slot cache
→ appointment (create+cancel) → pay callback + timeout MQ → queue tickets
→ admin CRUD + 3 个集成测试 + 压测脚本 → Docker + 前端 + README
```

---

## 1. 功能全景

### 顾客端 H5（角色 role=0）

| 功能 | 接口 | 核心要点 |
|---|---|---|
| 浏览门店 + 服务项 | `GET /api/stores`、`/api/stores/{id}/services` | 公开接口，无需登录 |
| 选时段 | `GET /api/stores/{id}/slots?serviceId=&date=` | Redis Cache Aside 缓存 + 空值防穿透 |
| 下单 | `POST /api/appointments` | Header `Idempotency-Key`；条件更新扣库存 |
| 模拟支付 | `POST /api/pay/mock` | 复用真实回调链路，服务端生成流水号+签名 |
| 我的预约 | `GET /api/appointments/mine` | JWT 身份，按状态筛选 |
| 取消预约 | `POST /api/appointments/{id}/cancel` | 仅待支付可取消；归属校验；库存回滚 |
| 排队取号 | `POST /api/queue/tickets` | Redis INCR 分桶 + DB 唯一索引兜底 |

### 管理端（角色 role=1 店员 / role=2 店长）

| 功能 | 接口 | 核心要点 |
|---|---|---|
| 预约列表 + 状态流转 | `GET /api/admin/appointments` + `POST /verify\|no-show\|cancel` | 条件更新 CAS，并发安全 |
| 服务项 CRUD | `/api/admin/services/**` | |
| 排班管理 | 直接维护 `time_slot_stock` 表（不建独立 schedule） | 避免双表同步 |
| 叫号面板 | `POST /api/admin/queue/tickets/{id}/call\|finish` | 过号延时 3min + 定时扫表兜底 |
| 今日概览 | `GET /api/admin/dashboard/today` | 覆盖索引，避免 N+1 |

### 认证

| 端点 | 说明 |
|---|---|
| `POST /api/auth/login` | 手机号正则 + BCrypt 校验；错误统一返回 `20002` 防手机号枚举 |
| `GET /api/auth/me` | 返回当前用户信息 |
| 鉴权 | HS256 JWT，claims: userId/phone/nickname/role/storeId；`Bearer` 头；120min 过期 |

---

## 2. 架构图（面试能徒手画）

```
                        ┌──────────────────────┐
                        │  顾客端 H5 / 管理端    │
                        │  (Vue3 + Vite + 部署) │
                        └───────────┬──────────┘
                                    │  HTTP (JWT Bearer)
                        ┌───────────▼──────────┐
                        │   Spring Boot 3      │
                        │   controller →       │
                        │   service（事务/状态机）│
                        │   → mapper            │
                        │                       │
                        │  ┌─ 支撑层 ─────────┐ │
                        │  │ Spring Security  │ │
                        │  │ + JWT Filter     │ │
                        │  │ Redis Cache AOP  │ │
                        │  │ RocketMQ Producer│ │
                        │  │ AfterCommit 监听器│ │
                        │  │ 全局异常/TraceId │ │
                        │  └─────────────────┘ │
                        └──┬─────────┬────────┘
                           │         │
              ┌────────────▼──┐  ┌──▼──────────────┐
              │   MySQL 8     │  │   Redis 7        │
              │  12 张表       │  │  缓存 + INCR     │
              └────────────┬──┘  └──┬──────────────┘
                           │         │
                    ┌──────▼─────────▼─────┐
                    │   RocketMQ 5.3       │
                    │   NameServer + Broker│
                    │   (延时消息)          │
                    └──────────────────────┘
```

**时序图（下单 → 支付 → 核销）**

```
顾客          Spring Boot                    MySQL          Redis          RocketMQ          管理端
  │               │                             │             │                │                 │
  │──POST /app→▶│                              │             │                │                 │
  │  Idem-Key    │──1. deductOne(slot) WHERE stock>0─────▶│             │                │                 │
  │               │   (条件更新：判断+自减合一)   │◀─ 1 row ──────│                │                 │
  │               │──2. insert booking_order ─────────────▶│             │                │                 │
  │               │   (uk_user_idempotent 唯一索引)          │             │                │                 │
  │◀── 返回 orderId=1 ─│                            │             │                │                 │
  │               │ 【事务提交】                     │             │                │                 │
  │               │──3. 延时消息 15min (PENDING_PAY→TIMEOUT)────────────────────▶│                 │
  │               │   (AfterCommit 监听器，不跟事务绑定)      │             │                │                 │
  │               │                               │             │                │                 │
  │──POST /pay/mock──▶│  (服务端生成 trade_no + 签名)  │             │                │                 │
  │               │──4. 状态机更新 WHERE status=PENDING_PAY──▶│             │                │                 │
  │               │   CAS 条件更新，防重复回调              │             │                │                 │
  │               │──5. 加积分 + 写 payment_record ─────────▶│             │                │                 │
  │               │──6. 积分 + 通知（AfterCommit） ─────────────────────────────────▶│                 │
  │               │                               │             │                │                 │
  │  (过一阵)      │                               │             │                │                 │
  │               │                               │             │                │──核销/verify──▶│
  │               │                               │             │                │──状态机──▶PAID → VERIFIED│
```

---

## 3. 12 张表 ER 关系

```
store ──1:N──▶ service_item ──N:M──▶ resource_info    (service_resource)
store ──1:N──▶ resource_info
resource_info ──1:N──▶ time_slot_stock    (slot 库存)
time_slot_stock ──1:N──▶ booking_order    (预约单)
booking_order ──1:N──▶ payment_record       (支付流水)
booking_order ──1:1──▶ queue_ticket         (排队号)
sys_user ──1:N──▶ booking_order / payment_record / queue_ticket / notification
```

**关键索引**（schema.sql 顶部有注释，面试可能被问"为什么加这些"）：

| 表 | 索引 | 作用 |
|---|---|---|
| `sys_user` | `uk_phone` | 手机号登录唯一 |
| `booking_order` | `uk_user_idempotent(user_id, idempotent_key)` | 幂等下单兜底；普通索引 `idx_user_status`、`idx_slot_date` |
| `payment_record` | `uk_trade_no` | 支付回调幂等 |
| `time_slot_stock` | `uk_resource_service_slot` | 资源+服务+日期+时段 联合唯一；CRUD 重跑不翻倍 |
| `queue_ticket` | `uk_store_service_date_seq` | 排队号不重复；`idx_ticket_code` 现场核销查号 |

---

## 4. 核心设计决策 × 6（面试高频追问）

### ① 并发扣库存：条件更新，不用 SELECT FOR UPDATE

```sql
UPDATE time_slot_stock SET remain_stock = remain_stock - 1
 WHERE id = #{slotId} AND remain_stock > 0
```

**为什么不 SELECT FOR UPDATE？**
SELECT FOR UPDATE 先加 S 锁读再升级 X 锁，锁持有时间长、死锁概率高、两次往返。条件更新把"判断够不够"和"减 1"合成一条 SQL，InnoDB 命中行直接加 X 锁，后到的事务等提交后再判断——一次往返搞定。

**单机上限？** 单行热点，InnoDB 行锁吞吐 ≈ 千级 QPS，200 并发压测全过。真到分布式规模才上 Redis + Lua。

### ② 幂等下单：两层防护

**外层**：Service 入口先查 `findByIdempotentKey(userId, key)`，存在直接返回。
**内层**：`uk_user_idempotent(user_id, idempotent_key)` 唯一索引——并发下两个线程同时过了第一层时，只有一个能 INSERT 成功，另一个抛 `DuplicateKeyException`，catch 后在外层重查返回对方已提交的订单。

**关键**：`DuplicateKeyException` 必须回滚库存（Spring 声明式事务自动处理），所以被拦截的线程不会留下库存扣减。

### ③ 订单状态机：条件更新自带 CAS

订单状态流转**必须**通过 `AppointmentService.changeStatus(from, to)` 方法，底层 SQL 带 `WHERE status = from`：

```sql
UPDATE booking_order SET status = #{to} WHERE id = #{id} AND status = #{from}
```

这样就天然是 CAS——并发下只有一方能改成功，另一方 rows affected = 0 → 抛 `ORDER_STATUS_ILLEGAL`。

**完整状态流**：
```
PENDING_PAY ──支付回调──▶ PAID ──核销──▶ VERIFIED ──完成──▶ FINISHED
      │                │
      ├──超时关单──▶ TIMEOUT_CANCELED
      └──用户取消──▶ CANCELED
                       │
                  (终态，不能再回)
```

**面试追问**：为什么 CANCELED 是终态？因为取消后可能库存已回滚，再改成 PAID 会导致库存双扣/双回滚。

### ④ 超时关单 / 过号：RocketMQ 延时消息 + 定时扫表 双保险

**为什么双保险？** MQ 会丢消息（broker 宕机、消费者挂了），定时扫表 `SELECT * FROM booking_order WHERE status = PENDING_PAY AND pay_deadline < NOW()` 每分钟跑一次兜底。

**延时消息多档补投**（RocketMQ 没有精确延时，只能 1s/5s/10s/30s/1m/2m/3m/4m/5m/6m/7m/8m/9m/10m/20m/30m/1h/2h 固定档位）：15min = 10min + 5min 补投；3min = 1m + 2m。

**关键**：延时消息在「事务提交后」才发（`@TransactionalEventListener(phase = AFTER_COMMIT)`），不跟下单事务绑定。如果事务回滚，MQ 消息根本没发。

### ⑤ 支付回调幂等：两层

**第一层**：`payment_record.uk_trade_no` 唯一索引——同一个 trade_no 的回调只会被处理一次。
**第二层**：状态机条件更新 `WHERE status = PENDING_PAY`——换了 trade_no 的重试也不会让一张已支付订单再推进。

**面试追问**：回调重复怎么不重复加积分/发通知？因为积分和通知挂在"订单从 PENDING_PAY → PAID"这个状态变更之后——状态没推进=后续逻辑都不执行。

### ⑥ Redis 缓存：Cache Aside + 穿透/击穿/雪崩

```java
@Cacheable(value = STORE_DETAIL, key = "#storeId", unless = "#result == null")
public StoreDetailVO getStoreDetail(Long storeId) { ... }
```

- **穿透**：空值也缓存（`unless = "#result == null"` 去掉，改成存空值标记，TTL 更短）；或布隆过滤器（demo 里没上，面试说"布隆过滤器"就行）
- **击穿**：热点 key 用互斥锁（Redisson `tryLock`）重建
- **雪崩**：TTL 末尾加随机值（比如 600s + rand(0, 60s)），避免同一时刻全过期

**绝不能缓存的**：待支付订单（实时性要求高、变化频繁）、`time_slot_stock.remain_stock`（库存一致性优先）

---

## 5. JWT 鉴权全链路

```
前端 Login.vue → POST /api/auth/login
                    │
                    ▼
              AuthService.login(phone, pwd)
                    │
                    ├── selectAuthByPhone() 查 sys_user（只查 phone/password/role/storeId/status）
                    ├── BCryptPasswordEncoder.matches() 校验
                    ├── status != 1 → 抛 USER_DISABLED (20003)
                    ├── 生成 JWT（HS256，claims: sub=userId, phone, nickname, role, storeId）
                    └── 返回 LoginVO（token + 用户信息）
                    
前端 axios 请求 → Authorization: Bearer <token>
                    │
                    ▼
              JwtAuthenticationFilter (OncePerRequestFilter)
                    │
                    ├── 只认 "Bearer " 前缀，否则放行（让 Security 的 path matcher 决定是否 401）
                    ├── JwtTokenProvider.parse()（捕获 JwtException/IllegalArgumentException → 返回 null）
                    └── 成功 → new UsernamePasswordAuthenticationToken(principal=LoginUser, authorities=[ROLE_xxx])
                              → SecurityContextHolder.setAuthentication()
                              
              业务 Controller
                    │
                    └── UserContext.currentUserId() 从 SecurityContext 取，彻底不信任任何请求头
                    
              SecurityConfig
                    ├── 白名单：/api/auth/login、/api/stores/**、/api/pay/callback、文档、actuator
                    ├── /api/admin/** → hasAnyRole("STAFF", "ADMIN")
                    ├── 其余 → authenticated
                    ├── 401/403 用 entryPoint/accessDeniedHandler 输出统一 Result（HTTP 200 + code）
                    └── SessionCreationPolicy.STATELESS
```

**面试追问**：JWT 怎么主动失效？纯无状态登出只删前端 token，服务端看不到。做法有：短过期 + Refresh Token；或 Redis 维护 jti 黑名单/用户 token 版本号。本项目 demo 用 120min 固定过期，面试说清取舍就行。

---

## 6. 踩过的坑 × 5（面试常被问"遇到过什么问题"）

### 坑 1：种子数据中文乱码
**现象**：直接在 PowerShell `mysql -e "INSERT ... 剪发"` 后，数据库里变成 `å°å...`。
**根因**：PowerShell 控制台默认 GBK 编码，mysql CLI 收到的中文已经被 GBK 解读一次，再写入 utf8mb4 列就是双重编码。`EFBFBD` 就是 UTF-8 的「替换字符」，说明部分字节彻底损坏。
**修复**：必须用纯 ASCII 的 `CONVERT(0x... USING utf8mb4)` 重写历史数据；以后批量中文一律用 `--default-character-set=utf8mb4` + .sql 文件。

### 坑 2：@Transactional + 多线程子任务 = 灾难
**现象**：并发扣库存测试里，50 线程全成功，库存扣到 -1。
**根因**：测试类加了类级 `@Transactional`，Spring 会把**整个 test 方法包成一个物理事务**。所有子线程都拿到同一个线程的 RR 快照——父线程刚 update 了库存，子线程看不到，以为还是 5，继续扣。
**修复**：去掉类级 @Transactional，每个测试方法自己开连接、自己 commit/rollback。事务是**连接级**的，子线程必须各开各的连接。

### 坑 3：时段已开始被拦截
**现象**：`mvn test` 里 `Order already started: 2026-11-22 10:00 is before now`。
**根因**：`prepareSlot()` 取最早的可售 slot，但 seed.sql 里最早的 slot 是今天 09:00。跑测试时已经 12:00 了，被 `create()` 里 `slot.start > LocalDateTime.now()` 校验拦下。
**修复**：`prepareSlot()` 只取 **tomorrow 及以后**的 slot。

### 坑 4：手工造订单要显式填 pay_deadline
**现象**：`DataIntegrityViolationException: Column 'pay_deadline' cannot be null`。
**根因**：`booking_order.pay_deadline NOT NULL` 没默认值。真实下单由 `AppointmentService.create()` 填 `now + 15min`，手工 insert 必须自己填。
**修复**：测试里造订单时显式 `order.setPayDeadline(LocalDateTime.now().plusMinutes(15))`。

### 坑 5：brokerIP1 在 Docker 网络里不能写 127.0.0.1
**现象**：容器化后 RocketMQ producer 发送 `timeout`，消费端收不到。
**根因**：`docker/rocketmq/broker.conf` 里 `brokerIP1=127.0.0.1`。broker 启动时把自己注册成 127.0.0.1，其他容器的 producer 连这个地址会连到**自己容器的 127.0.0.1**（不存在 broker）。
**修复**：改成 `brokerIP1=rocketmq-broker`——compose 内部 DNS 自动解析成 broker 容器的 IP。

---

## 7. 压测数据

```
python scripts/load_test_booking.py concurrency --slot-id 74

== 并发抢名额：200 线程抢 5 个名额 ==
  登录 200 个种子账号 ...
  请求数=200  HTTP200=200
  code 分布={40002: 195, 0: 5}
  耗时(ms) P50=161  P95=214  P99=220  max=226  avg=158
  [PASS] 全部 HTTP 200
  [PASS] 成功数 = 库存数（5/5）
  [PASS] 售罄数 = 195
  [PASS] 订单号不重复

python scripts/load_test_booking.py idempotent --slot-id 74

== 幂等验证：50 线程使用同一个 Idempotency-Key ==
  请求数=50  HTTP200=50
  code 分布={0: 50}
  耗时(ms) P50=141  P95=183  P99=186
  [PASS] 全部 HTTP 200
  [PASS] 只产生一个订单号
```

**集成测试（mvn test）三个全绿**：`deductOne_notOversold_underConcurrency`、`create_idempotentKey_onlyOneOrder`、`changeStatus_illegalTransition_throwsBizException`。

---

## 8. 高频面试题 20 问 + 标准答案要点

### 架构/整体

1. **你这个项目解决了什么问题？**
   单体下多门店预约场景的**高并发扣库存一致性**，靠条件更新把"判断+自减"合成一条 SQL；配套**状态机**保证订单状态只合法流转；**MQ + 定时双保险**做超时处理。单机可扛 200 并发压测。

2. **为什么选单体不选微服务？**
   校招项目规模小（用户量级万级、QPS 千级），微服务的注册中心、网关、分布式锁、调用链追踪全是过度设计。单体分层清晰，**用最朴素的手段（SQL 行锁 + 唯一索引 + 条件更新）就能扛住单机热点**，反而更能体现"知止"的工程判断力。

### 并发扣库存

3. **为什么条件更新比 SELECT FOR UPDATE 好？**
   - 一次往返（SELECT FOR UPDATE 要两次往返）
   - 锁持有时间短（命中后直接 X 锁，升级等待少）
   - 死锁概率低（统一顺序 UPDATE，不像 S→X 升级容易互相等）

4. **单机条件更新的并发上限是多少？瓶颈在哪里？**
   InnoDB 单行热点 ≈ 千级 QPS。瓶颈是 MySQL 自身的行锁队列——热点 slot 所有人抢同一行。再往上要 Redis + Lua 做集群外预判，但那是百万级 QPS 才需要。

### 幂等

5. **幂等为什么要两层？先查后插不够吗？**
   不够。并发下两个线程同时"先查"（都返回空），然后同时 INSERT，两个都成功，产生双订单。唯一索引是**最后一道防线**——"先查后插 + 唯一索引"组合覆盖了所有情况。

6. **DuplicateKeyException 后怎么处理？为什么不直接返回"重复请求"？**
   catch 后在外层**重查** `findByIdempotentKey`，返回对方已提交的订单 VO。这样调用方拿到的是"自己的订单"，业务流程继续走，不是报错退出。同时库存会被事务回滚（Spring 声明式事务默认回滚 RuntimeException），不会多扣。

### 状态机

7. **状态机怎么保证不非法流转？**
   `changeStatus(from, to)` 底层带 `WHERE status = from`——只有当前状态确实是 from 才更新成功。并发下只有一方能推进，另一方 rows affected=0 → 抛 `ORDER_STATUS_ILLEGAL`。这就是 CAS。

8. **并发下取消、支付、超时关单同时发生会怎样？**
   三者都是条件更新，同一行锁保证只有一个能推进。剩下两个会拿到 0 rows → 抛异常回滚。不会出现"库存被多还"或"状态被二次推进"。

### JWT/安全

9. **JWT 已经无状态了为什么还要 Security Filter？**
   JWT 只解决"凭证携带"，不解决"谁来授权"。Filter 只做解析 + 写入 SecurityContextHolder（ThreadLocal，请求结束自动清）；真正的路径/角色授权交给框架的 `authorizeHttpRequests`。

10. **401 和 403 为什么分开？为什么业务失败用 HTTP 200？**
    401=未认证（没 token/过期），前端踢回登录页；403=已认证但权限不足，只提示不踢人。这是协议层语义，保留原生状态码。业务失败（库存不足、状态不允许）是"请求被正常处理但业务不满足"，用响应体 code 表达、HTTP 200，便于统一解析和网关透传。

### RocketMQ 延时消息

11. **RocketMQ 延时消息怎么实现 15min？不是只有固定档位吗？**
    15min = 10min + 5min 两个延时消息补投。收到 10min 消息时发现订单还没支付，再投一个 5min；两次都没被支付就关单。遵循"宁可晚到不早到"原则。

12. **超时关单为什么要 MQ + 定时扫表双保险？**
    MQ 会丢（broker 宕机、consumer 挂了、生产端异常），定时扫表 `WHERE status=PENDING_PAY AND pay_deadline<NOW()` 每分钟一次兜底。**关键业务不能只信一个通道**。

13. **MQ 消息为什么要在事务提交后发？**
    如果跟下单事务绑定在同一方法里，事务回滚消息也会被消费（Spring TransactionSynchronization 里 commit 才发也不够——commit 瞬间宕机消息就丢了）。正确做法是 Spring 领域事件 `@TransactionalEventListener(phase = AFTER_COMMIT)`，监听器只在事务成功提交后才执行。

### 缓存

14. **什么是 Cache Aside？为什么选它？**
    读：先查缓存 → miss → 查 DB → 写缓存。写：先 DB → 再删缓存。读多写少的最常用模式，实现简单，**缓存和 DB 的最终一致性**能接受（写后短暂不一致窗口在预约场景没问题）。

15. **为什么不缓存 remain_stock？**
    库存变更频繁（下单/取消/超时关单都会改），而且**一致性要求高**——缓存里的库存过期了会导致超卖。库存扣减走 DB 条件更新才是对的，Redis 只缓存"门店详情"、"服务列表"这种读多写少的数据。

### 部署/压测

16. **docker compose up 一键起是怎么做到的？**
    MySQL 用 `/docker-entrypoint-initdb.d/` 挂载 schema.sql + seed.sql，首次启动自动执行。backend 用 `depends_on` + `healthcheck` 等 MySQL ready 了才启动。broker.conf 的 `brokerIP1` 必须改成 compose 服务名。

17. **压测前为什么要单独重置目标时段库存？**
    避免历史订单残留影响结果——如果库存被之前的压测扣过，新压测"200 抢 5"的断言就不准了。压测前用 UPDATE 把目标 slot 的 remain_stock 重置回 5、DELETE 该 slot 所有历史订单。

### 其他

18. **Spring Security 的白名单和路径匹配规则怎么写？**
    `requestMatchers("/api/auth/login", "/api/stores/**", "/api/pay/callback", ...).permitAll()`；`requestMatchers("/api/admin/**").hasAnyRole("STAFF", "ADMIN")`；最后 `anyRequest().authenticated()`。顺序很重要——specific 放前面。

19. **支付回调怎么防重放（恶意重复调用）？**
    两层：`payment_record.uk_trade_no` 唯一索引 + 订单状态机条件更新。**回调必须带签名**（demo 用服务端生成流水号模拟，生产是微信/支付宝 RSA 验签）。验签失败直接拒。

20. **你觉得这个项目还能怎么扩展？**
    1) 退款流程：`PAID → REFUNDING → REFUNDED`，复用状态机模式  
    2) 通知落库：notification 表 + Redis 未读计数  
    3) 单机 → 集群：库存改成 Redis + Lua 预判（因为条件更新锁行，分布式下所有请求打到同实例才能扛）  
    4) 管理端排班直接维护 time_slot_stock（已这么做）
