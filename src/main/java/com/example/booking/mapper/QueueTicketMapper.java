package com.example.booking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.booking.domain.QueueTicket;
import com.example.booking.dto.StatusCount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface QueueTicketMapper extends BaseMapper<QueueTicket> {

    /**
     * 查当天该门店该服务项已发放的最大序号。
     * 只在 Redis 计数器「冷启动」（当天第一次 INCR 返回 1）时调用：
     * Redis 被清空或重启后序号会从 1 重新开始，用 DB 最大值做种子，避免撞 uk_store_service_date_seq。
     */
    @Select("""
            SELECT COALESCE(MAX(seq_no), 0)
            FROM queue_ticket
            WHERE store_id = #{storeId}
              AND service_id = #{serviceId}
              AND ticket_date = #{ticketDate}
            """)
    long selectMaxSeq(@Param("storeId") Long storeId,
                      @Param("serviceId") Long serviceId,
                      @Param("ticketDate") LocalDate ticketDate);

    /** 今日取号按状态分组计数，走 idx_ticket_date_status */
    @Select("""
            <script>
            SELECT status, COUNT(*) AS cnt
            FROM queue_ticket
            WHERE ticket_date = CURDATE()
            <if test="storeId != null"> AND store_id = #{storeId} </if>
            GROUP BY status
            </script>
            """)
    List<StatusCount> countTodayByStatus(@Param("storeId") Long storeId);
}
