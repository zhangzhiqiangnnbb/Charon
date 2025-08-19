package com.lucas.lucasdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan("com.lucas.lucasdemo.repository")
public class LucasdemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(LucasdemoApplication.class, args);
    }
}
