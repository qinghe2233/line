-- =============================================================
-- 预约排队系统 · 演示 / 压测种子数据
--
-- 执行（必须带 utf8mb4，否则中文会被控制台 GBK 二次编码成乱码）：
--   mysql -uroot -p123456 --default-character-set=utf8mb4 < src/main/resources/db/seed.sql
--
-- 特性：
--   1) 可重复执行：唯一键冲突时更新关键字段，不会产生重复门店/服务/账号；
--   2) 时段以 CURDATE() 为基准生成未来 7 天，任何时候执行都能直接演示；
--   3) 重复执行会把时段库存重置为 total_stock=remain_stock=5，
--      压测/演示搞乱库存后，重跑本脚本即可恢复基线（仅限演示环境）。
--
-- 演示账号（密码均为 123456）：
--   店长 13900000000 / 店员 13900000001 / 顾客 13800001000、13800000002
-- 压测账号：
--   13800001001 ~ 13800001200，密码 123456
-- =============================================================

SET NAMES utf8mb4;
USE `booking_queue`;

-- -------------------------------------------------------------
-- 1. 演示账号（固定 ID，便于前端/文档引用）
-- password 列为 BCrypt("123456")，只存哈希，不可逆
-- -------------------------------------------------------------
INSERT INTO `sys_user` (`id`, `phone`, `password`, `nickname`, `role`, `store_id`, `status`, `points`) VALUES
  (10001, '13800001000', '$2a$10$NQyRiRrBgL/Hqrt7H3lOZeG0KL7uyimamcoFfmT3wfD8TnwlTXlii', '小张', 0, NULL, 1, 0),
  (10002, '13900000000', '$2a$10$NQyRiRrBgL/Hqrt7H3lOZeG0KL7uyimamcoFfmT3wfD8TnwlTXlii', '店长', 2, 1, 1, 0),
  (10003, '13900000001', '$2a$10$NQyRiRrBgL/Hqrt7H3lOZeG0KL7uyimamcoFfmT3wfD8TnwlTXlii', '店员小李', 1, 1, 1, 0),
  (10004, '13800000002', '$2a$10$NQyRiRrBgL/Hqrt7H3lOZeG0KL7uyimamcoFfmT3wfD8TnwlTXlii', '小王', 0, NULL, 1, 0)
ON DUPLICATE KEY UPDATE
  `password` = VALUES(`password`),
  `nickname` = VALUES(`nickname`),
  `role`     = VALUES(`role`),
  `store_id` = VALUES(`store_id`),
  `status`   = VALUES(`status`);

-- 200 个压测账号：13800001001 ~ 13800001200。
-- 并发压测要求每个线程一个独立用户，避免 uk_user_idempotent 之外的账号干扰
INSERT INTO `sys_user` (`phone`, `password`, `nickname`, `role`, `store_id`, `status`, `points`)
WITH RECURSIVE seq AS (
  SELECT 1001 AS n
  UNION ALL
  SELECT n + 1 FROM seq WHERE n < 1200
)
SELECT CONCAT('1380000', LPAD(n, 4, '0')),
       '$2a$10$NQyRiRrBgL/Hqrt7H3lOZeG0KL7uyimamcoFfmT3wfD8TnwlTXlii',
       CONCAT('压测用户', n - 1000),
       0, NULL, 1, 0
FROM seq
ON DUPLICATE KEY UPDATE
  `password` = VALUES(`password`),
  `status`   = 1;

-- -------------------------------------------------------------
-- 2. 门店
-- -------------------------------------------------------------
INSERT INTO `store` (`id`, `name`, `city`, `address`, `phone`, `business_start`, `business_end`, `status`) VALUES
  (1, '杭州西湖店', '杭州', '杭州市西湖区文三路100号', '0571-88880001', '09:00:00', '21:00:00', 1),
  (2, '杭州滨江店', '杭州', '杭州市滨江区江南大道588号', '0571-88880002', '09:00:00', '21:00:00', 1)
ON DUPLICATE KEY UPDATE
  `name`     = VALUES(`name`),
  `city`     = VALUES(`city`),
  `address`  = VALUES(`address`),
  `phone`    = VALUES(`phone`),
  `status`   = VALUES(`status`);

-- -------------------------------------------------------------
-- 3. 服务项（每家门店 4 个）
-- -------------------------------------------------------------
INSERT INTO `service_item`
  (`id`, `store_id`, `name`, `duration_minutes`, `buffer_minutes`, `price`, `description`, `sort_no`, `status`) VALUES
  (1, 1, '剪发',       60, 10,  88.00, '资深发型师剪发，含洗吹',       1, 1),
  (2, 1, '洗吹造型',   45, 10,  48.00, '洗头 + 吹风造型',             2, 1),
  (3, 1, '烫发',      120, 15, 288.00, '含药水与造型，时长约两小时',   3, 1),
  (4, 1, '头皮护理',   60, 10, 168.00, '深层清洁与头皮按摩',           4, 1),
  (5, 2, '精剪',       60, 10,  98.00, '店长级精剪',                   1, 1),
  (6, 2, '儿童剪发',   45, 10,  68.00, '12 岁以下儿童专属',            2, 1),
  (7, 2, '烫染套餐',  150, 15, 388.00, '烫发 + 染发套餐',              3, 1),
  (8, 2, '头皮SPA',    75, 10, 198.00, '舒缓型头皮 SPA',               4, 1)
ON DUPLICATE KEY UPDATE
  `store_id`         = VALUES(`store_id`),
  `name`             = VALUES(`name`),
  `duration_minutes` = VALUES(`duration_minutes`),
  `buffer_minutes`   = VALUES(`buffer_minutes`),
  `price`            = VALUES(`price`),
  `description`      = VALUES(`description`),
  `sort_no`          = VALUES(`sort_no`),
  `status`           = VALUES(`status`);

-- -------------------------------------------------------------
-- 4. 资源（技师 / 包间）
-- -------------------------------------------------------------
INSERT INTO `resource_info` (`id`, `store_id`, `name`, `type`, `description`, `status`) VALUES
  (1, 1, 'Tony老师',   1, '首席发型师', 1),
  (2, 1, 'Kevin老师',  1, '资深发型师', 1),
  (3, 2, 'Amy老师',    1, '店长',       1),
  (4, 2, '2号包间',    2, '独立包间',   1)
ON DUPLICATE KEY UPDATE
  `store_id`    = VALUES(`store_id`),
  `name`        = VALUES(`name`),
  `type`        = VALUES(`type`),
  `description` = VALUES(`description`),
  `status`      = VALUES(`status`);

-- -------------------------------------------------------------
-- 5. 资源-服务关联（多对多：每个资源都能做本店所有项目）
-- -------------------------------------------------------------
INSERT IGNORE INTO `service_resource` (`service_id`, `resource_id`) VALUES
  (1, 1), (2, 1), (3, 1), (4, 1),
  (1, 2), (2, 2), (3, 2), (4, 2),
  (5, 3), (6, 3), (7, 3), (8, 3),
  (5, 4), (6, 4), (7, 4), (8, 4);

-- -------------------------------------------------------------
-- 6. 时段库存：未来 7 天 × 每天 12 个整点时段 × 16 个资源-服务组合
--    每个时段 5 个名额；CURDATE() 保证脚本任何时候执行都有可约时段
--    先 INSERT IGNORE 保住已有行，再统一 UPDATE 把库存重置回 5：
--    拆成两条语句是因为 MySQL 的 INSERT ... SELECT 后直接跟
--    ON DUPLICATE KEY UPDATE 在部分 8.0 版本会报语法错误
-- -------------------------------------------------------------
INSERT IGNORE INTO `time_slot_stock`
  (`store_id`, `resource_id`, `service_id`, `slot_date`, `start_time`, `end_time`, `total_stock`, `remain_stock`, `status`)
SELECT c.store_id, c.resource_id, c.service_id,
       DATE_ADD(CURDATE(), INTERVAL d.n DAY),
       t.start_time, t.end_time,
       5, 5, 1
FROM (
  SELECT 1 AS store_id, 1 AS resource_id, 1 AS service_id UNION ALL
  SELECT 1, 1, 2 UNION ALL
  SELECT 1, 1, 3 UNION ALL
  SELECT 1, 1, 4 UNION ALL
  SELECT 1, 2, 1 UNION ALL
  SELECT 1, 2, 2 UNION ALL
  SELECT 1, 2, 3 UNION ALL
  SELECT 1, 2, 4 UNION ALL
  SELECT 2, 3, 5 UNION ALL
  SELECT 2, 3, 6 UNION ALL
  SELECT 2, 3, 7 UNION ALL
  SELECT 2, 3, 8 UNION ALL
  SELECT 2, 4, 5 UNION ALL
  SELECT 2, 4, 6 UNION ALL
  SELECT 2, 4, 7 UNION ALL
  SELECT 2, 4, 8
) c
CROSS JOIN (
  SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL
  SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
) d
CROSS JOIN (
  SELECT '09:00:00' AS start_time, '10:00:00' AS end_time UNION ALL
  SELECT '10:00:00', '11:00:00' UNION ALL
  SELECT '11:00:00', '12:00:00' UNION ALL
  SELECT '12:00:00', '13:00:00' UNION ALL
  SELECT '13:00:00', '14:00:00' UNION ALL
  SELECT '14:00:00', '15:00:00' UNION ALL
  SELECT '15:00:00', '16:00:00' UNION ALL
  SELECT '16:00:00', '17:00:00' UNION ALL
  SELECT '17:00:00', '18:00:00' UNION ALL
  SELECT '18:00:00', '19:00:00' UNION ALL
  SELECT '19:00:00', '20:00:00' UNION ALL
  SELECT '20:00:00', '21:00:00'
) t;

-- 把本次生成的时段库存重置回基线（压测后重跑本脚本即可恢复）
UPDATE `time_slot_stock`
   SET `total_stock` = 5, `remain_stock` = 5, `status` = 1
 WHERE `store_id` IN (1, 2)
   AND `slot_date` BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 6 DAY);

-- -------------------------------------------------------------
-- 7. 执行结果自检（控制台能看到数量与中文是否正常）
-- -------------------------------------------------------------
SELECT '门店' AS 表名, COUNT(*) AS 行数 FROM `store`
UNION ALL SELECT '服务项', COUNT(*) FROM `service_item`
UNION ALL SELECT '资源', COUNT(*) FROM `resource_info`
UNION ALL SELECT '可售时段', COUNT(*) FROM `time_slot_stock`
UNION ALL SELECT '账号', COUNT(*) FROM `sys_user`;
