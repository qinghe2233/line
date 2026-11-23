package com.example.booking.service;

import com.example.booking.common.CacheNames;
import com.example.booking.dto.AvailableSlotVO;
import com.example.booking.mapper.TimeSlotStockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 可约时段查询。
 * 这是预约页访问量最大的接口，按"门店 + 服务 + 日期"缓存。
 */
@Service
@RequiredArgsConstructor
public class SlotQueryService {

    private final TimeSlotStockMapper timeSlotStockMapper;

    /**
     * Key = booking:cache:v1:slot:day:{storeId}:{serviceId}:{yyyy-MM-dd}
     *
     * 缓存整个列表（含剩余名额展示值），TTL 5 分钟 + 0~60 秒随机：
     * - 下单/取消会主动删这个 key（CacheAside），所以正常情况不用等 TTL；
     * - TTL 只是兜底，保证即使删缓存失败，最多 5 分钟后数据也会自动变新；
     * - 空列表也会被缓存，随机 serviceId 刷接口不会每次都查库。
     *
     * 重要：列表里的 remainStock 只用于展示。真正"能不能约上"由下单时的
     * UPDATE ... WHERE remain_stock > 0 决定，绝不能拿缓存值做扣减判断。
     */
    @Cacheable(cacheNames = CacheNames.SLOT_DAY,
            key = "#storeId + ':' + #serviceId + ':' + #date",
            sync = true)
    public List<AvailableSlotVO> listAvailable(Long storeId, Long serviceId, LocalDate date) {
        return timeSlotStockMapper.selectAvailableSlots(storeId, serviceId, date);
    }
}
