package com.example.booking;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类。
 * 在这里统一声明 Mapper 扫描路径，避免每个 Mapper 接口都写 @Mapper。
 * @EnableScheduling：开启定时任务，用于超时关单的兜底扫表（MQ 消息丢失时的补偿）。
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.example.booking.mapper")
public class BookingQueueApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingQueueApplication.class, args);
    }
}
