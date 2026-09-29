package com.sky.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 餐饮平台智能客服服务入口
 * 独立于苍穹外卖（sky-server）进程，通过 HTTP 调用其只读接口
 */
@SpringBootApplication
public class ChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}
