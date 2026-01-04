package com.Charon.service.mq;

import com.Charon.dto.JobMessage;
import com.Charon.service.VideoCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.rocketmq.consumer.enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(topic = "video-code-topic", consumerGroup = "video-code-consumer-group")
@RequiredArgsConstructor
@Slf4j
public class JobConsumer implements RocketMQListener<JobMessage> {

    private final VideoCodeService videoCodeService;

    @Override
    public void onMessage(JobMessage message) {
        log.info("Received job from MQ: {}", message.getJobId());
        try {
            videoCodeService.executeJob(message);
        } catch (Exception e) {
            log.error("Error processing job {}", message.getJobId(), e);
        }
    }
}
