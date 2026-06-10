package com.nowait;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class Team6NowaitApp1Application {
    public static void main(String[] args) {
        SpringApplication.run(Team6NowaitApp1Application.class, args);
    }
}