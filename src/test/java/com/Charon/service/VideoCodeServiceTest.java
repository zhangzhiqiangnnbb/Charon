package com.Charon.service;

import com.Charon.dto.JobMessage;
import com.Charon.entity.VideoRecord;
import com.Charon.repository.VideoRecordRepository;
import com.Charon.service.command.SubmitJobCommand;
import com.Charon.service.mq.JobProducer;
import com.Charon.service.port.VideoEncoder;
import com.Charon.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoCodeServiceTest {

    @Mock
    private VideoRecordRepository repo;
    @Mock
    private StorageService storage;
    @Mock
    private JobRegistry jobs;
    @Mock
    private JobProducer jobProducer;
    @Mock
    private java.util.List<VideoEncoder> videoEncoders;

    @InjectMocks
    private VideoCodeService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "workdir", System.getProperty("java.io.tmpdir"));
    }

    @Test
    void submit() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", new byte[10]);
        when(repo.insert(any(VideoRecord.class))).thenReturn(1);
        doNothing().when(jobProducer).sendJob(any(JobMessage.class));

        SubmitJobCommand cmd = new SubmitJobCommand(
                file, 2, 60, "1080p", null, null, true, 20, "pass", "hint", null, "pass", null, null, "CPU"
        );

        Map<String, Object> result = service.submit(cmd);

        assertNotNull(result.get("jobId"));
        verify(repo).insert(any(VideoRecord.class));
        verify(jobProducer).sendJob(any(JobMessage.class));
    }
}
