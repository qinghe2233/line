package com.example.booking.controller;

import com.example.booking.common.BizException;
import com.example.booking.common.Result;
import com.example.booking.common.ResultCode;
import com.example.booking.dto.AvailableSlotVO;
import com.example.booking.dto.ServiceItemVO;
import com.example.booking.dto.StoreDetailVO;
import com.example.booking.service.SlotQueryService;
import com.example.booking.service.StoreQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 门店浏览接口（公开，无需登录）。
 * 缓存逻辑在 Service 层，Controller 只负责参数与返回体。
 */
@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreQueryService storeQueryService;
    private final SlotQueryService slotQueryService;

    /** 营业中门店列表：H5 选门店第一步 */
    @GetMapping
    public Result<List<StoreDetailVO>> list() {
        return Result.success(storeQueryService.listOpen());
    }

    /** 门店详情：缓存 booking:cache:v1:store:detail:{storeId} */
    @GetMapping("/{storeId}")
    public Result<StoreDetailVO> detail(@PathVariable Long storeId) {
        StoreDetailVO detail = storeQueryService.getDetail(storeId);
        if (detail == null) {
            // Service 返回 null 是为了让"空值"能进缓存，业务错误在 Controller 层抛
            throw new BizException(ResultCode.STORE_NOT_FOUND);
        }
        return Result.success(detail);
    }

    /** 某天可约时段：缓存 booking:cache:v1:slot:day:{storeId}:{serviceId}:{date} */
    @GetMapping("/{storeId}/slots")
    public Result<List<AvailableSlotVO>> slots(@PathVariable Long storeId,
                                               @RequestParam Long serviceId,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(slotQueryService.listAvailable(storeId, serviceId, date));
    }

    /** 门店已上架服务项：H5 选服务第二步 */
    @GetMapping("/{storeId}/services")
    public Result<List<ServiceItemVO>> services(@PathVariable Long storeId) {
        return Result.success(storeQueryService.listOnShelfServices(storeId));
    }
}
