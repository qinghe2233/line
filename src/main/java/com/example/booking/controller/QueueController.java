package com.example.booking.controller;

import com.example.booking.common.Result;
import com.example.booking.common.UserContext;
import com.example.booking.dto.QueueTicketVO;
import com.example.booking.dto.TakeTicketRequest;
import com.example.booking.service.QueueTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "05-排队（顾客端）")
@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueTicketService queueTicketService;

    @Operation(summary = "现场取号",
            description = "按「门店 + 服务项 + 当日」生成递增号，并发下由 Redis INCR 保证不重号")
    @PostMapping("/tickets")
    public Result<QueueTicketVO> take(@Valid @RequestBody TakeTicketRequest request) {
        return Result.success(queueTicketService.take(UserContext.currentUserId(), request));
    }

    @Operation(summary = "查询排队进度", description = "返回当前状态与「前面还有 N 人」")
    @GetMapping("/tickets/{id}")
    public Result<QueueTicketVO> progress(@PathVariable Long id) {
        return Result.success(queueTicketService.progress(id));
    }
}
