package com.example.booking.controller;

import com.example.booking.common.Result;
import com.example.booking.dto.MockPayRequest;
import com.example.booking.dto.PayCallbackRequest;
import com.example.booking.dto.PayCallbackVO;
import com.example.booking.service.PayCallbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付渠道回调入口（公开接口，靠验签保证来源可信）。
 * 重复回调也返回 code=0：渠道只认 HTTP 200 + 成功响应，否则会一直重试。
 */
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PayController {

    private final PayCallbackService payCallbackService;

    @PostMapping("/callback")
    public Result<PayCallbackVO> callback(@Valid @RequestBody PayCallbackRequest request) {
        return Result.success(payCallbackService.handle(request));
    }

    /**
     * 模拟支付：H5 支付页调用。
     * 只接收订单号，流水号/金额/签名全部服务端生成，不把签名密钥交给浏览器。
     */
    @PostMapping("/mock")
    public Result<PayCallbackVO> mockPay(@Valid @RequestBody MockPayRequest request) {
        return Result.success(payCallbackService.mockPay(request.orderNo()));
    }
}
