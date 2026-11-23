# 分 10 次提交：按模块粒度切，git log 能清晰看到项目演进

# ========== commit 1: 骨架 + 配置 ==========
git add pom.xml .gitignore
git add src/main/resources/application.yml
git add src/main/resources/application-local.yml
git add src/main/resources/application-docker.yml
git add src/main/resources/logback-spring.xml
git add src/main/java/com/example/booking/BookingQueueApplication.java
git add src/main/java/com/example/booking/common/Result.java
git add src/main/java/com/example/booking/common/BizException.java
git add src/main/java/com/example/booking/common/ResultCode.java
git add src/main/java/com/example/booking/common/GlobalExceptionHandler.java
git add src/main/java/com/example/booking/common/TraceIdFilter.java
git add src/main/java/com/example/booking/common/PageResult.java
git add src/main/java/com/example/booking/common/CacheNames.java
git add src/main/java/com/example/booking/common/MqConst.java
git add src/main/java/com/example/booking/config/MybatisPlusConfig.java
git add src/main/java/com/example/booking/config/MyMetaObjectHandler.java
git add src/main/java/com/example/booking/config/JacksonConfig.java
git add src/main/java/com/example/booking/config/Knife4jConfig.java
git add src/main/java/com/example/booking/config/RedisCacheConfig.java
git add src/main/java/com/example/booking/controller/PingController.java
git add src/main/java/com/example/booking/dto/PingVO.java
git commit -m "init: project skeleton (pom, profiles, common, config, health endpoint)"

# ========== commit 2: DB schema + enums ==========
git add src/main/resources/db/schema.sql
git add src/main/java/com/example/booking/domain/enums/OrderStatus.java
git add src/main/java/com/example/booking/domain/enums/TicketStatus.java
git add src/main/java/com/example/booking/domain/enums/UserRole.java
git add src/main/java/com/example/booking/domain/package-info.java
git commit -m "feat(db): 12 tables schema + Order/Ticket/User enums + state machine transitions"

# ========== commit 3: 种子数据 + 实体 + 基础 Mapper ==========
git add src/main/resources/db/seed.sql
git add src/main/java/com/example/booking/domain/Store.java
git add src/main/java/com/example/booking/domain/ServiceItem.java
git add src/main/java/com/example/booking/domain/ResourceInfo.java
git add src/main/java/com/example/booking/domain/TimeSlotStock.java
git add src/main/java/com/example/booking/domain/BookingOrder.java
git add src/main/java/com/example/booking/domain/PaymentRecord.java
git add src/main/java/com/example/booking/domain/QueueTicket.java
git add src/main/java/com/example/booking/domain/SysUser.java
git add src/main/java/com/example/booking/domain/SysUserAuth.java
git add src/main/java/com/example/booking/mapper/
git commit -m "feat(db): seed data + entities + MyBatis-Plus mappers"

# ========== commit 4: JWT 鉴权模块 ==========
git add src/main/java/com/example/booking/config/JwtProperties.java
git add src/main/java/com/example/booking/config/SecurityConfig.java
git add src/main/java/com/example/booking/security/
git add src/main/java/com/example/booking/common/LoginUser.java
git add src/main/java/com/example/booking/common/UserContext.java
git add src/main/java/com/example/booking/common/CacheEvictSupport.java
git add src/main/java/com/example/booking/service/AuthService.java
git add src/main/java/com/example/booking/controller/AuthController.java
git add src/main/java/com/example/booking/dto/LoginRequest.java
git add src/main/java/com/example/booking/dto/LoginVO.java
git commit -m "feat(auth): Spring Security + JWT + login/me endpoints"

# ========== commit 5: 门店查询公开接口 + 时段缓存服务 ==========
git add src/main/java/com/example/booking/service/StoreQueryService.java
git add src/main/java/com/example/booking/service/SlotQueryService.java
git add src/main/java/com/example/booking/controller/StoreController.java
git add src/main/java/com/example/booking/dto/StoreDetailVO.java
git add src/main/java/com/example/booking/dto/AvailableSlotVO.java
git add src/main/java/com/example/booking/dto/SlotVO.java
git add src/main/java/com/example/booking/dto/ServiceItemVO.java
git commit -m "feat(store): public store/slot endpoints + Redis cache (Cache Aside)"

# ========== commit 6: 预约下单核心（状态机、幂等、取消） ==========
git add src/main/java/com/example/booking/service/package-info.java
git add src/main/java/com/example/booking/service/AppointmentService.java
git add src/main/java/com/example/booking/controller/AppointmentController.java
git add src/main/java/com/example/booking/dto/CreateAppointmentRequest.java
git add src/main/java/com/example/booking/dto/AppointmentVO.java
git add src/main/java/com/example/booking/dto/MockPayRequest.java
git commit -m "feat(appointment): create (condition-update stock + idempotency), cancel (state machine)"

# ========== commit 7: 支付 + 超时关单 ==========
git add src/main/java/com/example/booking/service/PayCallbackService.java
git add src/main/java/com/example/booking/service/OrderTimeoutService.java
git add src/main/java/com/example/booking/controller/PayController.java
git add src/main/java/com/example/booking/dto/PayCallbackRequest.java
git add src/main/java/com/example/booking/dto/PayCallbackVO.java
git add src/main/java/com/example/booking/mq/
git add src/main/java/com/example/booking/event/
git add src/main/java/com/example/booking/task/
git commit -m "feat(pay): callback idempotency + RocketMQ delayed close + cron fallback"

# ========== commit 8: 排队叫号 ==========
git add src/main/java/com/example/booking/service/QueueTicketService.java
git add src/main/java/com/example/booking/service/QueueSequenceService.java
git add src/main/java/com/example/booking/controller/QueueController.java
git add src/main/java/com/example/booking/dto/TakeTicketRequest.java
git add src/main/java/com/example/booking/dto/QueueTicketVO.java
git commit -m "feat(queue): Redis INCR sequence + state machine + 3min delayed no-show"

# ========== commit 9: 管理端 CRUD + 后端集成测试 + 压测脚本 ==========
git add src/main/java/com/example/booking/service/AdminManageService.java
git add src/main/java/com/example/booking/service/AdminQueryService.java
git add src/main/java/com/example/booking/controller/AdminController.java
git add src/main/java/com/example/booking/dto/AdminAppointmentQuery.java
git add src/main/java/com/example/booking/dto/AdminAppointmentVO.java
git add src/main/java/com/example/booking/dto/ServiceItemRequest.java
git add src/main/java/com/example/booking/dto/SlotSaveRequest.java
git add src/main/java/com/example/booking/dto/StatusCount.java
git add src/main/java/com/example/booking/dto/TodayOverviewVO.java
git add src/test/
git add scripts/
git commit -m "feat(admin): staff/admin CRUD + 3 integration tests + load script"

# ========== commit 10: Docker + 前端 + README ==========
git add Dockerfile
git add docker-compose.yml
git add docker/
git add frontend/
git add README.md
git commit -m "chore: Docker Compose one-click start + Vue3 H5/Admin + README"
