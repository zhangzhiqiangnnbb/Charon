package com.Charon.service;

import com.Charon.dto.JobMessage;
import com.Charon.entity.VideoRecord;
import com.Charon.repository.VideoRecordRepository;
import com.Charon.service.command.SubmitJobCommand;
import com.Charon.service.mq.JobProducer;
import com.Charon.service.port.VideoEncoder;
import com.Charon.service.port.VideoEncodingRequest;
import com.Charon.storage.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VideoCodeService {

    @Value("${app.workdir:${user.home}/video-qrcode}")
    private String workdir;

    private final VideoRecordRepository repo;
    private final StorageService storage;
    private final JobRegistry jobs;
    private final JobProducer jobProducer;
    private final List<VideoEncoder> videoEncoders;
    private static final Logger log = LoggerFactory.getLogger(VideoCodeService.class);

    public Map<String, Object> submit(SubmitJobCommand cmd) throws IOException {
        String jobId = UUID.randomUUID().toString();
        Path jobDir = Path.of(workdir, jobId);
        Files.createDirectories(jobDir);

        MultipartFile file = cmd.file();
        Path inputZip = jobDir.resolve("input.zip");
        Files.copy(file.getInputStream(), inputZip, StandardCopyOption.REPLACE_EXISTING);

        Path obfPath = null;
        MultipartFile obfuscationFile = cmd.obfuscationFile();
        if (obfuscationFile != null && !obfuscationFile.isEmpty()) {
            obfPath = jobDir.resolve("obfuscation.bin");
            Files.copy(obfuscationFile.getInputStream(), obfPath, StandardCopyOption.REPLACE_EXISTING);
        }
        String obfArg = obfPath != null ? obfPath.toString() : null;

        VideoRecord vr = new VideoRecord();
        vr.setJobId(jobId);
        vr.setOriginalFileName(file.getOriginalFilename());
        vr.setOriginalFileSize(file.getSize());
        vr.setGridN(cmd.gridN());
        vr.setFps(cmd.fps());
        vr.setResolution(cmd.resolution());
        vr.setEnableFec(cmd.enableFec());
        vr.setFecParityPercent(cmd.fecParityPercent());
        vr.setPublicKeyHint(cmd.publicKeyHint());
        vr.setPrivateKeyFrameIndex(cmd.privateKeyFrameIndex());
        vr.setObfuscationSeed(cmd.obfuscationSeed());
        vr.setProcessingMode(cmd.processingMode() != null ? cmd.processingMode() : "CPU");
        vr.setStatus(VideoRecord.ProcessStatus.PROCESSING);
        vr.setCreatedAt(LocalDateTime.now());
        repo.insert(vr);

        JobMessage msg = JobMessage.builder()
                .jobId(jobId)
                .jobDirPath(jobDir.toString())
                .inputZipPath(inputZip.toString())
                .obfArg(obfArg)
                .gridN(cmd.gridN())
                .fps(cmd.fps())
                .resolution(cmd.resolution())
                .enableFec(cmd.enableFec())
                .fecParityPercent(cmd.fecParityPercent())
                .passphrase(cmd.passphrase())
                .publicKeyHint(cmd.publicKeyHint())
                .privateKeyFrameIndex(cmd.privateKeyFrameIndex())
                .privateKeyFramePassword(cmd.privateKeyFramePassword())
                .width(cmd.width())
                .height(cmd.height())
                .processingMode(vr.getProcessingMode())
                .build();

        jobProducer.sendJob(msg);

        Map<String, Object> res = new HashMap<>();
        res.put("jobId", jobId);
        return res;
    }

    public void executeJob(JobMessage msg) {
        String jid = msg.getJobId();
        Path jobDirFinal = Path.of(msg.getJobDirPath());
        VideoRecord vr = repo.findByJobId(jid).orElse(null);
        if (vr == null) {
            log.error("Job record not found: {}", jid);
            return;
        }

        try {
            log.info("job start {}", jid);
            jobs.setProgress(jid, 5, "SAVED_INPUT");

            Path outputVideo = jobDirFinal.resolve("output.mp4");
            Path manifestJson = jobDirFinal.resolve("manifest.json");

            VideoEncodingRequest request = new VideoEncodingRequest(
                    jid,
                    Path.of(msg.getInputZipPath()),
                    outputVideo,
                    manifestJson,
                    msg.getObfArg(),
                    msg.getGridN(),
                    msg.getFps(),
                    msg.getResolution(),
                    msg.getWidth(),
                    msg.getHeight(),
                    msg.isEnableFec(),
                    msg.getFecParityPercent(),
                    msg.getPassphrase(),
                    msg.getPublicKeyHint(),
                    msg.getPrivateKeyFrameIndex(),
                    msg.getPrivateKeyFramePassword(),
                    msg.getProcessingMode()
            );

            // Select strategy
            VideoEncoder encoder = videoEncoders.stream()
                    .filter(e -> e.supports(msg.getProcessingMode()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No encoder found for mode: " + msg.getProcessingMode()));

            // Encoder handles process execution
            encoder.encode(request);

            jobs.setProgress(jid, 60, "PERSISTING");
            String videoStorePath;
            String manifestStorePath;
            try (InputStream vin = Files.newInputStream(outputVideo);
                 InputStream min = Files.newInputStream(manifestJson)) {
                videoStorePath = storage.store(vin, jid + ".mp4");
                manifestStorePath = storage.store(min, jid + "-manifest.json");
            }

            Integer outputFrameCount = null;
            try {
                ObjectMapper om = new ObjectMapper();
                JsonNode node = om.readTree(Files.readString(manifestJson));
                if (node.has("frames")) {
                    outputFrameCount = node.get("frames").asInt();
                }
            } catch (Exception ignored) {}

            vr.complete(videoStorePath, manifestStorePath, Files.size(outputVideo), outputFrameCount);
            repo.updateById(vr);
            jobs.setProgress(jid, 100, "DONE");
            deleteQuietly(jobDirFinal);
            log.info("job done {}", jid);

        } catch (Exception e) {
            vr.fail(e.getMessage());
            repo.updateById(vr);
            deleteQuietly(jobDirFinal);
            log.error("job error {} {}", jid, e.getMessage());
        }
    }

    private static void deleteQuietly(Path dir) {
        try {
            if (dir != null && Files.exists(dir)) {
                Files.walk(dir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {}
                        });
            }
        } catch (IOException ignored) {}
    }
}
