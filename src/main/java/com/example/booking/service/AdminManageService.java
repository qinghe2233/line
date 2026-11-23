package com.example.booking.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.booking.common.BizException;
import com.example.booking.common.ResultCode;
import com.example.booking.domain.ResourceInfo;
import com.example.booking.domain.ServiceItem;
import com.example.booking.domain.Store;
import com.example.booking.domain.TimeSlotStock;
import com.example.booking.dto.ServiceItemRequest;
import com.example.booking.dto.ServiceItemVO;
import com.example.booking.dto.SlotSaveRequest;
import com.example.booking.dto.SlotVO;
import com.example.booking.mapper.ResourceInfoMapper;
import com.example.booking.mapper.ServiceItemMapper;
import com.example.booking.mapper.StoreMapper;
import com.example.booking.mapper.TimeSlotStockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端基础数据维护：服务项、资源、排班时段。
 *
 * 为什么不单独建 schedule 实体：
 * 顾客能不能约完全由 time_slot_stock 决定，排班页直接维护它就是最短链路；
 * 再建一套 schedule 表反而要做两表同步，属于过度设计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminManageService {

    private static final int DEFAULT_STATUS_ON = 1;

    private final ServiceItemMapper serviceItemMapper;
    private final TimeSlotStockMapper timeSlotStockMapper;
    private final ResourceInfoMapper resourceInfoMapper;
    private final StoreMapper storeMapper;

    /* ---------------- 服务项 ---------------- */

    public List<ServiceItemVO> listServices(Long storeId) {
        List<ServiceItem> items = serviceItemMapper.selectList(Wrappers.<ServiceItem>lambdaQuery()
                .eq(storeId != null, ServiceItem::getStoreId, storeId)
                .orderByAsc(ServiceItem::getSortNo)
                .orderByAsc(ServiceItem::getId));
        return items.stream().map(ServiceItemVO::of).toList();
    }

    @Transactional
    public ServiceItemVO createService(ServiceItemRequest request) {
        requireStore(request.storeId());

        ServiceItem item = new ServiceItem();
        applyServiceFields(item, request);
        try {
            serviceItemMapper.insert(item);
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.DUPLICATE_REQUEST);
        }
        log.info("新增服务项 storeId={} serviceId={} name={}", item.getStoreId(), item.getId(), item.getName());
        return ServiceItemVO.of(item);
    }

    @Transactional
    public ServiceItemVO updateService(Long id, ServiceItemRequest request) {
        ServiceItem item = serviceItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ResultCode.SERVICE_NOT_FOUND);
        }
        requireStore(request.storeId());
        applyServiceFields(item, request);
        serviceItemMapper.updateById(item);
        log.info("修改服务项 serviceId={} status={}", id, item.getStatus());
        return ServiceItemVO.of(item);
    }

    private void applyServiceFields(ServiceItem item, ServiceItemRequest request) {
        item.setStoreId(request.storeId());
        item.setName(request.name().trim());
        item.setDurationMinutes(request.durationMinutes());
        item.setBufferMinutes(request.bufferMinutes() == null ? 0 : request.bufferMinutes());
        item.setPrice(request.price());
        item.setDescription(request.description() == null ? "" : request.description());
        item.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        item.setStatus(request.status() == null ? DEFAULT_STATUS_ON : request.status());
    }

    /* ---------------- 资源（排班下拉框） ---------------- */

    public List<ResourceInfo> listResources(Long storeId) {
        return resourceInfoMapper.selectList(Wrappers.<ResourceInfo>lambdaQuery()
                .eq(storeId != null, ResourceInfo::getStoreId, storeId)
                .orderByAsc(ResourceInfo::getId));
    }

    /* ---------------- 排班时段 ---------------- */

    public List<SlotVO> listSlots(Long storeId, LocalDate date) {
        List<TimeSlotStock> slots = timeSlotStockMapper.selectList(Wrappers.<TimeSlotStock>lambdaQuery()
                .eq(storeId != null, TimeSlotStock::getStoreId, storeId)
                .eq(date != null, TimeSlotStock::getSlotDate, date)
                .orderByAsc(TimeSlotStock::getSlotDate)
                .orderByAsc(TimeSlotStock::getStartTime));
        if (slots.isEmpty()) {
            return Collections.emptyList();
        }

        // 两个小字典表，一次批量查出来补名称，避免每行查一次（N+1）
        List<Long> serviceIds = slots.stream().map(TimeSlotStock::getServiceId).distinct().toList();
        List<Long> resourceIds = slots.stream().map(TimeSlotStock::getResourceId).distinct().toList();
        Map<Long, String> serviceNames = serviceItemMapper.selectBatchIds(serviceIds).stream()
                .collect(Collectors.toMap(ServiceItem::getId, ServiceItem::getName));
        Map<Long, String> resourceNames = resourceInfoMapper.selectBatchIds(resourceIds).stream()
                .collect(Collectors.toMap(ResourceInfo::getId, ResourceInfo::getName));

        return slots.stream()
                .map(slot -> new SlotVO(
                        slot.getId(), slot.getStoreId(), slot.getServiceId(),
                        serviceNames.get(slot.getServiceId()),
                        slot.getResourceId(), resourceNames.get(slot.getResourceId()),
                        slot.getSlotDate(), slot.getStartTime(), slot.getEndTime(),
                        slot.getTotalStock(), slot.getRemainStock(), slot.getStatus()))
                .toList();
    }

    @Transactional
    public SlotVO createSlot(SlotSaveRequest request) {
        validateSlotRequest(request);

        TimeSlotStock slot = new TimeSlotStock();
        slot.setStoreId(request.storeId());
        slot.setServiceId(request.serviceId());
        slot.setResourceId(request.resourceId());
        slot.setSlotDate(request.slotDate());
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        slot.setTotalStock(request.totalStock());
        // 新建时段默认全部可售；后续下单只动 remain_stock
        slot.setRemainStock(request.totalStock());
        slot.setStatus(request.status() == null ? DEFAULT_STATUS_ON : request.status());
        try {
            timeSlotStockMapper.insert(slot);
        } catch (DuplicateKeyException e) {
            // uk_resource_service_slot 兜底，避免同一资源同一时间重复排班
            throw new BizException(ResultCode.DUPLICATE_REQUEST, "该资源在该时间已有排班");
        }
        log.info("新增排班 slotId={} storeId={} serviceId={} date={} {}~{}",
                slot.getId(), slot.getStoreId(), slot.getServiceId(),
                slot.getSlotDate(), slot.getStartTime(), slot.getEndTime());
        return toSlotVO(slot);
    }

    /**
     * 修改排班：总名额变化时同步平移剩余名额，并夹在 [0, total] 之间。
     * 例如总名额 5→8，剩余 2→5；总名额 8→3，剩余 5→0，避免出现剩余大于总数。
     */
    @Transactional
    public SlotVO updateSlot(Long id, SlotSaveRequest request) {
        TimeSlotStock slot = timeSlotStockMapper.selectById(id);
        if (slot == null) {
            throw new BizException(ResultCode.SLOT_NOT_FOUND);
        }
        validateSlotRequest(request);

        int delta = request.totalStock() - slot.getTotalStock();
        int remain = Math.max(0, Math.min(request.totalStock(), slot.getRemainStock() + delta));

        slot.setServiceId(request.serviceId());
        slot.setResourceId(request.resourceId());
        slot.setSlotDate(request.slotDate());
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        slot.setTotalStock(request.totalStock());
        slot.setRemainStock(remain);
        slot.setStatus(request.status() == null ? DEFAULT_STATUS_ON : request.status());
        try {
            timeSlotStockMapper.updateById(slot);
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.DUPLICATE_REQUEST, "该资源在该时间已有排班");
        }
        log.info("修改排班 slotId={} total={} remain={} status={}", id, slot.getTotalStock(),
                slot.getRemainStock(), slot.getStatus());
        return toSlotVO(slot);
    }

    private void validateSlotRequest(SlotSaveRequest request) {
        requireStore(request.storeId());
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BizException(ResultCode.PARAM_INVALID, "结束时间必须晚于开始时间");
        }
        ServiceItem service = serviceItemMapper.selectById(request.serviceId());
        if (service == null || !request.storeId().equals(service.getStoreId())) {
            throw new BizException(ResultCode.SERVICE_NOT_FOUND);
        }
        ResourceInfo resource = resourceInfoMapper.selectById(request.resourceId());
        if (resource == null || !request.storeId().equals(resource.getStoreId())) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
    }

    private void requireStore(Long storeId) {
        Store store = storeMapper.selectById(storeId);
        if (store == null) {
            throw new BizException(ResultCode.STORE_NOT_FOUND);
        }
    }

    /** 单条保存时没有批量字典，直接查两次名称 */
    private SlotVO toSlotVO(TimeSlotStock slot) {
        ServiceItem service = serviceItemMapper.selectById(slot.getServiceId());
        ResourceInfo resource = resourceInfoMapper.selectById(slot.getResourceId());
        return new SlotVO(
                slot.getId(), slot.getStoreId(), slot.getServiceId(),
                service == null ? null : service.getName(),
                slot.getResourceId(), resource == null ? null : resource.getName(),
                slot.getSlotDate(), slot.getStartTime(), slot.getEndTime(),
                slot.getTotalStock(), slot.getRemainStock(), slot.getStatus());
    }
}
