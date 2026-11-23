package com.example.booking.controller;

import com.example.booking.common.Result;
import com.example.booking.dto.PingVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 连通性测试接口。
 * 作用：不依赖数据库和 MQ，验证 Web 层、统一返回体、traceId、Swagger 是否正常。
 */
@Tag(name = "00-系统")
@RestController
@RequestMapping("/api/v1")
public class PingController {

    @Operation(summary = "连通性测试", description = "返回服务端时间，用于验证服务是否正常启动")
    @GetMapping("/ping")
    public Result<PingVO> ping() {
        return Result.success(new PingVO("pong", LocalDateTime.now()));
    }
}
