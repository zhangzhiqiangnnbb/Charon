package com.Charon.service.mq;

import com.Charon.dto.JobMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public void sendJob(JobMessage message) {
        log.info("Sending job to MQ: {}", message.getJobId());
        rocketMQTemplate.send("video-code-topic", MessageBuilder.withPayload(message).build());
    }
}
