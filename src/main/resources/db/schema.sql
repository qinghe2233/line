-- =============================================================
-- 预约排队系统 · 数据库初始化脚本
-- 适用：MySQL 8.0.30+ / InnoDB / utf8mb4
-- 约定：DATETIME 均为业务本地时间（Asia/Shanghai），金额单位=元
-- =============================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `booking_queue`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `booking_queue`;

-- -------------------------------------------------------------
-- 1. 用户表（顾客 / 店员 / 店长 统一账号体系）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`         BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `phone`      VARCHAR(20)      NOT NULL                COMMENT '手机号，登录账号',
  `password`   VARCHAR(100)     NOT NULL                COMMENT 'BCrypt 加密后的密码',
  `nickname`   VARCHAR(50)      NOT NULL DEFAULT ''     COMMENT '昵称',
  `avatar`     VARCHAR(255)     NOT NULL DEFAULT ''     COMMENT '头像地址',
  `role`       TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '角色：0-顾客 1-店员 2-店长/管理员',
  `store_id`   BIGINT UNSIGNED  NULL                    COMMENT '所属门店，仅店员/店长有值，顾客为 NULL',
  `status`     TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '状态：0-禁用 1-正常',
  `points`     INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '积分余额，支付成功后原子累加',
  `created_at` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`    TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '逻辑删除：0-未删 1-已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_store_role` (`store_id`, `role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表（顾客+员工统一账号）';

-- -------------------------------------------------------------
-- 2. 门店表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `store`;
CREATE TABLE `store` (
  `id`             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`           VARCHAR(64)      NOT NULL                COMMENT '门店名称',
  `city`           VARCHAR(32)      NOT NULL DEFAULT ''     COMMENT '所在城市，用于按城市筛选',
  `address`        VARCHAR(255)     NOT NULL DEFAULT ''     COMMENT '详细地址',
  `phone`          VARCHAR(20)      NOT NULL DEFAULT ''     COMMENT '门店电话',
  `business_start` TIME             NOT NULL DEFAULT '09:00:00' COMMENT '每日开始营业时间',
  `business_end`   TIME             NOT NULL DEFAULT '21:00:00' COMMENT '每日结束营业时间',
  `status`         TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '状态：0-停业 1-营业中',
  `created_at`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '逻辑删除：0-未删 1-已删',
  PRIMARY KEY (`id`),
  KEY `idx_city_status` (`city`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店表';

-- -------------------------------------------------------------
-- 3. 服务项表（剪发 60 分钟、烫发 120 分钟…）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `service_item`;
CREATE TABLE `service_item` (
  `id`               BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `store_id`         BIGINT UNSIGNED   NOT NULL                COMMENT '所属门店',
  `name`             VARCHAR(64)       NOT NULL                COMMENT '服务项名称',
  `duration_minutes` SMALLINT UNSIGNED NOT NULL                COMMENT '服务时长（分钟）',
  `buffer_minutes`   SMALLINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '服务后缓冲（分钟），用于清洁/交接，占库存但不对外展示',
  `price`            DECIMAL(10,2)     NOT NULL DEFAULT 0.00   COMMENT '标准价格（元）',
  `description`      VARCHAR(500)      NOT NULL DEFAULT ''     COMMENT '服务描述',
  `sort_no`          INT               NOT NULL DEFAULT 0      COMMENT '展示排序，越小越前',
  `status`           TINYINT UNSIGNED  NOT NULL DEFAULT 1      COMMENT '状态：0-下架 1-上架',
  `created_at`       DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`       DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          TINYINT UNSIGNED  NOT NULL DEFAULT 0      COMMENT '逻辑删除：0-未删 1-已删',
  PRIMARY KEY (`id`),
  KEY `idx_store_status_sort` (`store_id`, `status`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务项目表';

-- -------------------------------------------------------------
-- 4. 资源表（技师 / 房间 / 工位）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `resource_info`;
CREATE TABLE `resource_info` (
  `id`          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `store_id`    BIGINT UNSIGNED  NOT NULL                COMMENT '所属门店',
  `name`        VARCHAR(64)      NOT NULL                COMMENT '资源名称，如「Tony老师」「2号包间」',
  `type`        TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '类型：1-技师 2-房间/工位',
  `description` VARCHAR(255)     NOT NULL DEFAULT ''     COMMENT '备注，如技师职级',
  `status`      TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '状态：0-不可用 1-可排班',
  `created_at`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '逻辑删除：0-未删 1-已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_store_name` (`store_id`, `name`),
  KEY `idx_store_type_status` (`store_id`, `type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资源表（技师/房间）';

-- -------------------------------------------------------------
-- 5. 资源-服务关联表（多对多：谁能做哪些项目）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `service_resource`;
CREATE TABLE `service_resource` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `service_id`  BIGINT UNSIGNED NOT NULL                COMMENT '服务项 ID',
  `resource_id` BIGINT UNSIGNED NOT NULL                COMMENT '资源 ID',
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_service` (`resource_id`, `service_id`),
  KEY `idx_service_resource` (`service_id`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资源可提供的服务关联表';

-- -------------------------------------------------------------
-- 6. 排班表（某资源某天，可分多班段）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `schedule`;
CREATE TABLE `schedule` (
  `id`          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `store_id`    BIGINT UNSIGNED  NOT NULL                COMMENT '门店 ID（冗余，便于按门店查排班）',
  `resource_id` BIGINT UNSIGNED  NOT NULL                COMMENT '资源 ID',
  `work_date`   DATE             NOT NULL                COMMENT '排班日期',
  `start_time`  TIME             NOT NULL                COMMENT '班段开始时间',
  `end_time`    TIME             NOT NULL                COMMENT '班段结束时间',
  `status`      TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '状态：1-正常 2-请假 3-调休',
  `created_at`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_date_start` (`resource_id`, `work_date`, `start_time`),
  KEY `idx_store_date` (`store_id`, `work_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资源排班表';

-- -------------------------------------------------------------
-- 7. 时段库存表（排班切分后的可售名额）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `time_slot_stock`;
CREATE TABLE `time_slot_stock` (
  `id`           BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `store_id`     BIGINT UNSIGNED  NOT NULL                COMMENT '门店 ID（冗余，列表页按门店过滤）',
  `resource_id`  BIGINT UNSIGNED  NOT NULL                COMMENT '资源 ID',
  `service_id`   BIGINT UNSIGNED  NOT NULL                COMMENT '服务项 ID',
  `slot_date`    DATE             NOT NULL                COMMENT '时段所属日期',
  `start_time`   TIME             NOT NULL                COMMENT '时段开始时间',
  `end_time`     TIME             NOT NULL                COMMENT '时段结束时间（含缓冲）',
  `total_stock`  INT UNSIGNED     NOT NULL                COMMENT '该时段总名额',
  `remain_stock` INT UNSIGNED     NOT NULL                COMMENT '剩余名额，扣减时用 remain_stock >= 1 做原子判断',
  `status`       TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '状态：0-已关闭/停售 1-可售',
  `created_at`   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_service_slot` (`resource_id`, `service_id`, `slot_date`, `start_time`),
  KEY `idx_service_date_remain` (`service_id`, `slot_date`, `status`, `remain_stock`),
  KEY `idx_store_date` (`store_id`, `slot_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='时段库存表';

-- -------------------------------------------------------------
-- 8. 预约单表（核心，含幂等键 + 状态机）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `booking_order`;
CREATE TABLE `booking_order` (
  `id`             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no`       VARCHAR(32)      NOT NULL                COMMENT '业务单号，对外展示与对账',
  `idempotent_key` VARCHAR(64)      NOT NULL                COMMENT '客户端幂等键（一次下单一个 UUID）',
  `user_id`        BIGINT UNSIGNED  NOT NULL                COMMENT '下单用户',
  `store_id`       BIGINT UNSIGNED  NOT NULL                COMMENT '门店 ID',
  `service_id`     BIGINT UNSIGNED  NOT NULL                COMMENT '服务项 ID',
  `resource_id`    BIGINT UNSIGNED  NOT NULL                COMMENT '资源 ID',
  `slot_id`        BIGINT UNSIGNED  NOT NULL                COMMENT '时段库存 ID，取消/超时按此回滚',
  `slot_date`      DATE             NOT NULL                COMMENT '预约日期快照',
  `start_time`     TIME             NOT NULL                COMMENT '开始时间快照',
  `end_time`       TIME             NOT NULL                COMMENT '结束时间快照',
  `service_name`   VARCHAR(64)      NOT NULL DEFAULT ''     COMMENT '服务名快照，防止服务改名后历史订单失真',
  `amount`         DECIMAL(10,2)    NOT NULL DEFAULT 0.00   COMMENT '应付金额（元）',
  `paid_amount`    DECIMAL(10,2)    NOT NULL DEFAULT 0.00   COMMENT '实付金额（元）',
  `refund_amount`  DECIMAL(10,2)    NOT NULL DEFAULT 0.00   COMMENT '已退金额（元）',
  `cancel_fee`     DECIMAL(10,2)    NOT NULL DEFAULT 0.00   COMMENT '取消违约金（元）',
  `status`         TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '状态：0-待支付 10-已支付 20-已核销 30-已完成 35-爽约 40-用户取消 50-超时取消 60-退款中 70-已退款',
  `pay_deadline`   DATETIME         NOT NULL                COMMENT '支付截止时间=创建时间+15分钟',
  `pay_time`       DATETIME         NULL                    COMMENT '支付成功时间',
  `verify_time`    DATETIME         NULL                    COMMENT '到店核销时间',
  `finish_time`    DATETIME         NULL                    COMMENT '服务完成时间',
  `cancel_time`    DATETIME         NULL                    COMMENT '取消时间',
  `cancel_reason`  VARCHAR(255)     NOT NULL DEFAULT ''     COMMENT '取消原因',
  `remark`         VARCHAR(255)     NOT NULL DEFAULT ''     COMMENT '顾客备注',
  `created_at`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `updated_at`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_user_idempotent` (`user_id`, `idempotent_key`),
  KEY `idx_user_status_created` (`user_id`, `status`, `created_at`),
  KEY `idx_store_date_status` (`store_id`, `slot_date`, `status`),
  -- 管理端「今日概览」/跨门店按日期统计：WHERE slot_date = CURDATE()，没有它只能全表扫
  KEY `idx_slot_date_status` (`slot_date`, `status`),
  KEY `idx_status_deadline` (`status`, `pay_deadline`),
  KEY `idx_slot_id` (`slot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约单表';

-- -------------------------------------------------------------
-- 9. 支付流水表（一笔订单可有多条支付尝试）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `payment_record`;
CREATE TABLE `payment_record` (
  `id`            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `pay_no`        VARCHAR(32)      NOT NULL                COMMENT '支付单号',
  `order_id`      BIGINT UNSIGNED  NOT NULL                COMMENT '预约单 ID',
  `order_no`      VARCHAR(32)      NOT NULL                COMMENT '预约单号（冗余，便于对账）',
  `user_id`       BIGINT UNSIGNED  NOT NULL                COMMENT '付款用户',
  `channel`       TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '渠道：1-模拟余额 2-微信 3-支付宝',
  `amount`        DECIMAL(10,2)    NOT NULL DEFAULT 0.00   COMMENT '支付金额（元）',
  `trade_no`      VARCHAR(64)      NULL                    COMMENT '三方交易流水号，回调幂等依据',
  `status`        TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '状态：0-待支付 1-成功 2-失败 3-已退款',
  `paid_at`       DATETIME         NULL                    COMMENT '支付成功时间',
  `refund_amount` DECIMAL(10,2)    NOT NULL DEFAULT 0.00   COMMENT '退款金额（元）',
  `refund_at`     DATETIME         NULL                    COMMENT '退款完成时间',
  `remark`        VARCHAR(255)     NOT NULL DEFAULT ''     COMMENT '备注/失败原因',
  `created_at`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_no` (`pay_no`),
  UNIQUE KEY `uk_trade_no` (`trade_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付流水表';

-- -------------------------------------------------------------
-- 10. 排队号表（到店取号 / 店员叫号）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `queue_ticket`;
CREATE TABLE `queue_ticket` (
  `id`               BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `ticket_no`        VARCHAR(16)      NOT NULL                COMMENT '展示号，如 A023',
  `seq_no`           INT UNSIGNED     NOT NULL                COMMENT '当日序号，用于排序叫号',
  `store_id`         BIGINT UNSIGNED  NOT NULL                COMMENT '门店 ID',
  `user_id`          BIGINT UNSIGNED  NOT NULL                COMMENT '取号用户',
  `service_id`       BIGINT UNSIGNED  NULL                    COMMENT '意向服务项，可为空',
  `booking_order_id` BIGINT UNSIGNED  NULL                    COMMENT '关联预约单，现场取号可为空',
  `ticket_date`      DATE             NOT NULL                COMMENT '取号日期',
  `status`           TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '状态：0-等待 1-已叫号 2-服务中 3-已完成 4-过号',
  `call_staff_id`    BIGINT UNSIGNED  NULL                    COMMENT '叫号店员 ID',
  `called_at`        DATETIME         NULL                    COMMENT '叫号时间',
  `finished_at`      DATETIME         NULL                    COMMENT '完成时间',
  `created_at`       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '取号时间',
  `updated_at`       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_store_date_ticket` (`store_id`, `ticket_date`, `ticket_no`),
  -- 号码按「门店 + 服务项 + 当日」递增，唯一索引必须带 service_id，
  -- 否则两个服务项当天都从 1 开始会互相撞号
  UNIQUE KEY `uk_store_service_date_seq` (`store_id`, `service_id`, `ticket_date`, `seq_no`),
  KEY `idx_store_date_status_seq` (`store_id`, `ticket_date`, `status`, `seq_no`),
  -- 「前面还有 N 人」：store + service + date + status 定位分组，seq_no 做范围比较
  KEY `idx_store_service_date_status_seq` (`store_id`, `service_id`, `ticket_date`, `status`, `seq_no`),
  -- 叫号超时兜底扫描：WHERE status = 1 AND called_at < now - 3min
  KEY `idx_status_called` (`status`, `called_at`),
  -- 跨门店今日取号统计：WHERE ticket_date = CURDATE()
  KEY `idx_ticket_date_status` (`ticket_date`, `status`),
  KEY `idx_user_date` (`user_id`, `ticket_date`),
  KEY `idx_booking_order` (`booking_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='排队号表';

-- -------------------------------------------------------------
-- 11. 通知表（站内信，MQ 消费落库）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `notification`;
CREATE TABLE `notification` (
  `id`          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT UNSIGNED  NOT NULL                COMMENT '接收用户',
  `order_id`    BIGINT UNSIGNED  NULL                    COMMENT '关联预约单，可为空',
  `type`        TINYINT UNSIGNED NOT NULL                COMMENT '类型：1-预约成功 2-支付成功 3-即将到号 4-已取消 5-退款完成',
  `title`       VARCHAR(100)     NOT NULL DEFAULT ''     COMMENT '标题',
  `content`     VARCHAR(500)     NOT NULL DEFAULT ''     COMMENT '正文',
  `biz_key`     VARCHAR(64)      NULL                    COMMENT '业务幂等键，如 order_no，配合 uk 防 MQ 重复消费',
  `status`      TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '阅读状态：0-未读 1-已读',
  `send_status` TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '发送状态：0-待发 1-已发 2-失败',
  `read_at`     DATETIME         NULL                    COMMENT '阅读时间',
  `created_at`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_type` (`biz_key`, `type`),
  KEY `idx_user_status_created` (`user_id`, `status`, `created_at`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知表';

-- -------------------------------------------------------------
-- 12. 操作日志表（追加写，不更新不删除）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `operator_id`   BIGINT UNSIGNED NOT NULL                COMMENT '操作人 ID，0 表示系统自动',
  `operator_name` VARCHAR(50)     NOT NULL DEFAULT ''     COMMENT '操作人名称快照',
  `store_id`      BIGINT UNSIGNED NULL                    COMMENT '门店 ID，系统级操作为空',
  `biz_type`      VARCHAR(32)     NOT NULL                COMMENT '业务类型：BOOKING/PAYMENT/QUEUE/SLOT',
  `biz_id`        VARCHAR(64)     NOT NULL                COMMENT '业务主键，如 order_no',
  `action`        VARCHAR(64)     NOT NULL                COMMENT '动作：CREATE/PAY/CANCEL/VERIFY/CALL',
  `before_data`   JSON            NULL                    COMMENT '变更前快照',
  `after_data`    JSON            NULL                    COMMENT '变更后快照',
  `ip`            VARCHAR(45)     NOT NULL DEFAULT ''     COMMENT '操作 IP',
  `user_agent`    VARCHAR(255)    NOT NULL DEFAULT ''     COMMENT '客户端 UA',
  `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`, `biz_id`, `created_at`),
  KEY `idx_operator_created` (`operator_id`, `created_at`),
  KEY `idx_store_created` (`store_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志表';
