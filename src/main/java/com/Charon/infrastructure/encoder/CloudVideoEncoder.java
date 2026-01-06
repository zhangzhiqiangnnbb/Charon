package com.Charon.infrastructure.encoder;

import com.Charon.service.port.VideoEncoder;
import com.Charon.service.port.VideoEncodingRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;

@Component
public class CloudVideoEncoder implements VideoEncoder {

    private static final Logger log = LoggerFactory.getLogger(CloudVideoEncoder.class);

    @Override
    public boolean supports(String mode) {
        return "CLOUD".equalsIgnoreCase(mode);
    }

    @Override
    public String encode(VideoEncodingRequest request) throws Exception {
        log.info("Starting Cloud Transcoding for Job: {}", request.jobId());
        
        // Simulation of cloud process
        // 1. Upload input.zip to Cloud Storage (S3/OSS)
        log.info("[Cloud] Uploading {} to bucket...", request.inputZip());
        Thread.sleep(1000); // Simulate network delay
        
        // 2. Trigger Cloud Function / Transcoding Service
        log.info("[Cloud] Triggering remote transcoding task (Grid={}, FPS={})...", request.gridN(), request.fps());
        Thread.sleep(2000); // Simulate processing time
        
        // 3. Download result
        log.info("[Cloud] Downloading results...");
        
        // Since this is a mock, we need to generate a dummy output file so the flow continues
        // In a real implementation, we would download the actual result
        if (!Files.exists(request.outputVideo())) {
            Files.createFile(request.outputVideo());
            Files.writeString(request.outputVideo(), "Simulated Cloud Video Content");
        }
        if (!Files.exists(request.manifestJson())) {
            Files.createFile(request.manifestJson());
            Files.writeString(request.manifestJson(), "{\"frames\": 100, \"mock\": true}");
        }
        
        return "Cloud transcoding completed successfully (Simulated)";
    }
}
