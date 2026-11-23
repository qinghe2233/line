package com.example.booking.controller;

import com.example.booking.common.PageResult;
import com.example.booking.common.Result;
import com.example.booking.common.UserContext;
import com.example.booking.domain.ResourceInfo;
import com.example.booking.dto.AdminAppointmentQuery;
import com.example.booking.dto.AdminAppointmentVO;
import com.example.booking.dto.QueueTicketVO;
import com.example.booking.dto.ServiceItemRequest;
import com.example.booking.dto.ServiceItemVO;
import com.example.booking.dto.SlotSaveRequest;
import com.example.booking.dto.SlotVO;
import com.example.booking.dto.TodayOverviewVO;
import com.example.booking.service.AdminManageService;
import com.example.booking.service.AdminQueryService;
import com.example.booking.service.AppointmentService;
import com.example.booking.service.QueueTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理端（店员/店长）。
 * 当前用 X-User-Id 占位登录态；接入 JWT 后在这里加 @PreAuthorize("hasAnyRole('STAFF','ADMIN')") 即可，
 * 业务层不需要改。
 */
@Tag(name = "06-管理端")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminQueryService adminQueryService;
    private final AdminManageService adminManageService;
    private final AppointmentService appointmentService;
    private final QueueTicketService queueTicketService;

    @Operation(summary = "预约列表（多条件筛选 + 分页）")
    @GetMapping("/appointments")
    public Result<PageResult<AdminAppointmentVO>> listAppointments(AdminAppointmentQuery query) {
        return Result.success(adminQueryService.pageAppointments(query));
    }

    @Operation(summary = "到店核销", description = "已支付 -> 已核销")
    @PostMapping("/appointments/{id}/verify")
    public Result<Void> verify(@PathVariable Long id) {
        appointmentService.verify(id);
        return Result.success();
    }

    @Operation(summary = "标记爽约", description = "已支付 -> 爽约")
    @PostMapping("/appointments/{id}/no-show")
    public Result<Void> markNoShow(@PathVariable Long id) {
        appointmentService.markNoShow(id);
        return Result.success();
    }

    @Operation(summary = "今日叫号列表", description = "按门店/状态筛选，按序号升序返回")
    @GetMapping("/queue/tickets")
    public Result<List<QueueTicketVO>> listTickets(
            @Parameter(description = "门店 ID，不传表示全部门店") @RequestParam(required = false) Long storeId,
            @Parameter(description = "状态：0-等待 1-叫号中 3-已完成 4-过号") @RequestParam(required = false) Integer status) {
        return Result.success(queueTicketService.listToday(storeId, status));
    }

    @Operation(summary = "叫号", description = "等待中 -> 叫号中，3 分钟未完成自动过号")
    @PostMapping("/queue/tickets/{id}/call")
    public Result<QueueTicketVO> call(@PathVariable Long id) {
        return Result.success(queueTicketService.call(id, UserContext.currentUserId()));
    }

    @Operation(summary = "完成服务", description = "叫号中 -> 已完成")
    @PostMapping("/queue/tickets/{id}/finish")
    public Result<QueueTicketVO> finish(@PathVariable Long id) {
        return Result.success(queueTicketService.finish(id));
    }

    @Operation(summary = "今日概览", description = "今日预约各状态数量 + 今日取号各状态数量")
    @GetMapping("/dashboard/today")
    public Result<TodayOverviewVO> today(
            @Parameter(description = "门店 ID，不传表示全部门店")
            @RequestParam(required = false) Long storeId) {
        return Result.success(adminQueryService.todayOverview(storeId));
    }

    /* ---------------- 服务项管理 ---------------- */

    @Operation(summary = "服务项列表", description = "包含已下架，用于后台维护")
    @GetMapping("/services")
    public Result<List<ServiceItemVO>> listServices(@RequestParam(required = false) Long storeId) {
        return Result.success(adminManageService.listServices(storeId));
    }

    @Operation(summary = "新增服务项")
    @PostMapping("/services")
    public Result<ServiceItemVO> createService(@Valid @RequestBody ServiceItemRequest request) {
        return Result.success(adminManageService.createService(request));
    }

    @Operation(summary = "修改服务项", description = "含上下架（status=0/1）")
    @PutMapping("/services/{id}")
    public Result<ServiceItemVO> updateService(@PathVariable Long id,
                                               @Valid @RequestBody ServiceItemRequest request) {
        return Result.success(adminManageService.updateService(id, request));
    }

    /* ---------------- 排班管理 ---------------- */

    @Operation(summary = "资源列表", description = "排班页选择技师/房间")
    @GetMapping("/resources")
    public Result<List<ResourceInfo>> listResources(@RequestParam(required = false) Long storeId) {
        return Result.success(adminManageService.listResources(storeId));
    }

    @Operation(summary = "排班列表", description = "按门店/日期查询时段库存，排班页主列表")
    @GetMapping("/slots")
    public Result<List<SlotVO>> listSlots(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(adminManageService.listSlots(storeId, date));
    }

    @Operation(summary = "新增排班时段")
    @PostMapping("/slots")
    public Result<SlotVO> createSlot(@Valid @RequestBody SlotSaveRequest request) {
        return Result.success(adminManageService.createSlot(request));
    }

    @Operation(summary = "修改排班时段", description = "改总名额时同步平移剩余名额")
    @PutMapping("/slots/{id}")
    public Result<SlotVO> updateSlot(@PathVariable Long id, @Valid @RequestBody SlotSaveRequest request) {
        return Result.success(adminManageService.updateSlot(id, request));
    }
}
