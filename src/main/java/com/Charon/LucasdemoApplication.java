package com.Charon;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.Charon.repository")
public class LucasdemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(LucasdemoApplication.class, args);
    }
}
