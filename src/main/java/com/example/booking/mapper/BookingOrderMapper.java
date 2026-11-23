package com.example.booking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.booking.domain.BookingOrder;
import com.example.booking.dto.StatusCount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 预约单 Mapper。单表 CRUD 全部使用 MyBatis-Plus 条件构造器，无需 XML。
 */
public interface BookingOrderMapper extends BaseMapper<BookingOrder> {

    /**
     * 今日预约按状态分组计数（管理端概览）。
     * 一条 GROUP BY 代替 6 条 COUNT(*)，少 5 次数据库往返；
     * 条件只用 slot_date（今日），走 idx_slot_date_status。
     */
    @Select("""
            <script>
            SELECT status, COUNT(*) AS cnt
            FROM booking_order
            WHERE slot_date = CURDATE()
            <if test="storeId != null"> AND store_id = #{storeId} </if>
            GROUP BY status
            </script>
            """)
    List<StatusCount> countTodayByStatus(@Param("storeId") Long storeId);
}
