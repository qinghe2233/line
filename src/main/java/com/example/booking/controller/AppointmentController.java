package com.example.booking.controller;

import com.example.booking.common.PageResult;
import com.example.booking.common.Result;
import com.example.booking.common.UserContext;
import com.example.booking.dto.AppointmentVO;
import com.example.booking.dto.CreateAppointmentRequest;
import com.example.booking.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "01-预约")
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @Operation(summary = "创建预约单",
            description = "同一笔下单请求必须携带相同的 Idempotency-Key，重复提交会返回同一张订单")
    @PostMapping
    public Result<AppointmentVO> create(
            @Parameter(description = "幂等键，客户端生成（UUID），同一次下单重试必须保持不变", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateAppointmentRequest request) {
        return Result.success(appointmentService.create(UserContext.currentUserId(), idempotencyKey, request));
    }

    @Operation(summary = "我的预约", description = "按当前用户分页查询，可按状态筛选；H5「我的预约」使用")
    @GetMapping("/mine")
    public Result<PageResult<AppointmentVO>> mine(
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，最大 50") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "订单状态，不传表示全部") @RequestParam(required = false) Integer status) {
        return Result.success(appointmentService.pageMine(UserContext.currentUserId(), status, page, size));
    }

    @Operation(summary = "取消预约",
            description = "仅待支付订单可取消；只能取消自己的订单；取消成功后回滚时段库存")
    @PostMapping("/{orderId}/cancel")
    public Result<Void> cancel(
            @Parameter(description = "订单 ID") @PathVariable Long orderId,
            @Parameter(description = "取消原因，不传使用默认文案")
            @RequestParam(required = false) String reason) {
        appointmentService.cancel(UserContext.currentUserId(), orderId, reason);
        return Result.success();
    }
}
