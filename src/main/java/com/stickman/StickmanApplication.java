package com.stickman;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * StickmanApplication — Điểm khởi động Spring Boot
 * Chức năng: Khởi động toàn bộ backend (REST API + WebSocket + AI Engine)
 */
@SpringBootApplication
@EnableScheduling
public class StickmanApplication {
    public static void main(String[] args) {
        SpringApplication.run(StickmanApplication.class, args);
    }
}
