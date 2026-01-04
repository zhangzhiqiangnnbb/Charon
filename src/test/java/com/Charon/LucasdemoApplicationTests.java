package com.Charon;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "app.rocketmq.consumer.enabled=false")
class LucasdemoApplicationTests {

    @MockBean
    private RocketMQTemplate rocketMQTemplate;

    @Test
    void contextLoads() {
    }

}
