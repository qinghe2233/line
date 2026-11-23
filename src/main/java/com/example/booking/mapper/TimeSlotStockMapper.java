package com.example.booking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.booking.domain.TimeSlotStock;
import com.example.booking.dto.AvailableSlotVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

public interface TimeSlotStockMapper extends BaseMapper<TimeSlotStock> {

    /**
     * 查询某门店、某服务、某天仍可预约的时段。
     * 只返回未来的时段（今天则要求开始时间晚于当前时间），避免把已经开始的时段展示给用户。
     * 注意：这里查出的 remain_stock 是展示用快照，是否真的能约上由 deductOne 的原子更新决定。
     */
    @Select("""
            SELECT s.id            AS slotId,
                   s.resource_id   AS resourceId,
                   r.name          AS resourceName,
                   s.start_time    AS startTime,
                   s.end_time      AS endTime,
                   s.remain_stock  AS remainStock
              FROM time_slot_stock s
              JOIN resource_info r ON r.id = s.resource_id AND r.status = 1
             WHERE s.store_id = #{storeId}
               AND s.service_id = #{serviceId}
               AND s.slot_date = #{date}
               AND s.status = 1
               AND s.remain_stock > 0
               AND TIMESTAMP(s.slot_date, s.start_time) > NOW()
             ORDER BY s.start_time
            """)
    List<AvailableSlotVO> selectAvailableSlots(@Param("storeId") Long storeId,
                                               @Param("serviceId") Long serviceId,
                                               @Param("date") LocalDate date);

    /**
     * 原子扣减库存。
     * 关键点：判断条件和自减在同一条 UPDATE 里完成，InnoDB 对命中行加排他锁，
     * 并发时后到的事务会等前一事务提交后再判断 remain_stock，因此不会超卖。
     * 绝不要改成"先 select 再 update"，那需要额外的行锁或版本号才能保证正确。
     *
     * @return 1 表示扣减成功，0 表示已售罄或时段被关闭
     */
    @Update("""
            UPDATE time_slot_stock
               SET remain_stock = remain_stock - 1,
                   updated_at = NOW()
             WHERE id = #{slotId}
               AND remain_stock > 0
               AND status = 1
            """)
    int deductOne(@Param("slotId") Long slotId);

    /**
     * 回滚库存（取消/超时关单使用）。
     * remain_stock < total_stock 防止重复回滚把库存加超。
     *
     * @return 1 表示回滚成功，0 表示该时段已满（说明已经被回滚过）
     */
    @Update("""
            UPDATE time_slot_stock
               SET remain_stock = remain_stock + 1,
                   updated_at = NOW()
             WHERE id = #{slotId}
               AND remain_stock < total_stock
            """)
    int restoreOne(@Param("slotId") Long slotId);
}
