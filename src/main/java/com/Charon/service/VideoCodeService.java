package com.Charon.service;

import com.Charon.dto.JobMessage;
import com.Charon.entity.VideoRecord;
import com.Charon.repository.VideoRecordRepository;
import com.Charon.service.mq.JobProducer;
import com.Charon.storage.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class VideoCodeService {

    @Value("${app.python.cmd:python}")
    private String pythonCmd;

    @Value("${app.ffmpeg.cmd:ffmpeg}")
    private String ffmpegCmd;

    @Value("${app.workdir:${user.home}/video-qrcode}")
    private String workdir;

    private final VideoRecordRepository repo;
    private final StorageService storage;
    private final JobRegistry jobs;
    private final JobProducer jobProducer;
    private static final Logger log = LoggerFactory.getLogger(VideoCodeService.class);

    public VideoCodeService(VideoRecordRepository repo, StorageService storage, JobRegistry jobs, JobProducer jobProducer) {
        this.repo = repo;
        this.storage = storage;
        this.jobs = jobs;
        this.jobProducer = jobProducer;
    }

    public Map<String, Object> submit(MultipartFile file,
                                       int gridN,
                                       int fps,
                                       String resolution,
                                       Integer width,
                                       Integer height,
                                       boolean enableFec,
                                       Integer fecParityPercent,
                                       String passphrase,
                                       String publicKeyHint,
                                       Integer privateKeyFrameIndex,
                                       String privateKeyFramePassword,
                                       Integer obfuscationSeed,
                                       MultipartFile obfuscationFile) throws IOException {
        String jobId = UUID.randomUUID().toString();
        Path jobDir = Path.of(workdir, jobId);
        Files.createDirectories(jobDir);

        // 保存上传文件到临时job目录 | Save uploaded file to a temporary job directory
        Path inputZip = jobDir.resolve("input.zip");
        Files.copy(file.getInputStream(), inputZip, StandardCopyOption.REPLACE_EXISTING);
        Path obfPath = null;
        if (obfuscationFile != null && !obfuscationFile.isEmpty()) {
            obfPath = jobDir.resolve("obfuscation.bin");
            Files.copy(obfuscationFile.getInputStream(), obfPath, StandardCopyOption.REPLACE_EXISTING);
        }
        String obfArg = obfPath != null ? obfPath.toString() : null;

        // 预建数据库记录（PROCESSING） | Pre-create database record (PROCESSING)
        VideoRecord vr = new VideoRecord();
        vr.setJobId(jobId);
        vr.setOriginalFileName(file.getOriginalFilename());
        vr.setOriginalFileSize(file.getSize());
        vr.setGridN(gridN);
        vr.setFps(fps);
        vr.setResolution(resolution);
        vr.setEnableFec(enableFec);
        vr.setFecParityPercent(fecParityPercent);
        vr.setPublicKeyHint(publicKeyHint);
        vr.setPrivateKeyFrameIndex(privateKeyFrameIndex);
        vr.setObfuscationSeed(obfuscationSeed);
        vr.setStatus(VideoRecord.ProcessStatus.PROCESSING);
        vr.setCreatedAt(LocalDateTime.now());
        repo.insert(vr);

        JobMessage msg = JobMessage.builder()
                .jobId(jobId)
                .jobDirPath(jobDir.toString())
                .inputZipPath(inputZip.toString())
                .obfArg(obfArg)
                .gridN(gridN)
                .fps(fps)
                .resolution(resolution)
                .enableFec(enableFec)
                .fecParityPercent(fecParityPercent)
                .passphrase(passphrase)
                .publicKeyHint(publicKeyHint)
                .privateKeyFrameIndex(privateKeyFrameIndex)
                .privateKeyFramePassword(privateKeyFramePassword)
                .width(width)
                .height(height)
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
            String scriptPath = Path.of("scripts", "encode_qr_video.py").toAbsolutePath().toString();
            ProcessBuilder pb = new ProcessBuilder(
                    pythonCmd,
                    scriptPath,
                    "--input", msg.getInputZipPath(),
                    "--output", outputVideo.toString(),
                    "--manifest", manifestJson.toString(),
                    "--grid", String.valueOf(msg.getGridN()),
                    "--fps", String.valueOf(msg.getFps()),
                    "--resolution", msg.getResolution(),
                    "--enable-fec", String.valueOf(msg.isEnableFec()),
                    "--fec-ratio", String.valueOf(msg.getFecParityPercent() == null ? 0.2 : Math.max(0.15, Math.min(0.35, msg.getFecParityPercent() / 100.0))),
                    "--passphrase", msg.getPassphrase(),
                    "--pubkey-hint", msg.getPublicKeyHint(),
                    "--privkey-frame", msg.getPrivateKeyFrameIndex() == null ? "0" : String.valueOf(msg.getPrivateKeyFrameIndex()),
                    "--privkey-frame-pass", msg.getPrivateKeyFramePassword()
            );
            pb.environment().put("FFMPEG_CMD", ffmpegCmd);
            if (msg.getWidth() != null && msg.getHeight() != null) {
                pb.command().add("--width");
                pb.command().add(String.valueOf(msg.getWidth()));
                pb.command().add("--height");
                pb.command().add(String.valueOf(msg.getHeight()));
            }
            if (msg.getObfArg() != null) {
                pb.command().add("--obfuscation");
                pb.command().add(msg.getObfArg());
            }
            pb.directory(java.nio.file.Paths.get("").toAbsolutePath().toFile());
            pb.redirectErrorStream(true);

            Process p = pb.start();
            jobs.register(jid, p);
            jobs.setProgress(jid, 20, "ENCODING");
            String logStr = readProcessOutputLimited(p.getInputStream(), 32768);
            int code = p.waitFor();
            jobs.remove(jid);

            if (code != 0) {
                vr.setStatus(VideoRecord.ProcessStatus.FAILED);
                vr.setErrorMessage(logStr);
                repo.updateById(vr);
                deleteQuietly(jobDirFinal);
                log.error("job failed {} code={}", jid, code);
                return;
            }

            jobs.setProgress(jid, 60, "PERSISTING");
            String videoStorePath;
            String manifestStorePath;
            try (InputStream vin = Files.newInputStream(outputVideo);
                 InputStream min = Files.newInputStream(manifestJson)) {
                videoStorePath = storage.store(vin, jid + ".mp4");
                manifestStorePath = storage.store(min, jid + "-manifest.json");
            }

            try {
                ObjectMapper om = new ObjectMapper();
                JsonNode node = om.readTree(Files.readString(manifestJson));
                if (node.has("frames")) {
                    vr.setOutputFrameCount(node.get("frames").asInt());
                }
            } catch (Exception ignored) {}

            vr.setStoragePath(videoStorePath);
            vr.setManifestPath(manifestStorePath);
            vr.setOutputVideoSize(Files.size(outputVideo));
            vr.setStatus(VideoRecord.ProcessStatus.COMPLETED);
            vr.setCompletedAt(LocalDateTime.now());
            repo.updateById(vr);
            jobs.setProgress(jid, 100, "DONE");
            deleteQuietly(jobDirFinal);
            log.info("job done {}", jid);
        } catch (Exception e) {
            vr.setStatus(VideoRecord.ProcessStatus.FAILED);
            vr.setErrorMessage(e.getMessage());
            repo.updateById(vr);
            deleteQuietly(jobDirFinal);
            log.error("job error {} {}", jid, e.getMessage());
        }
    }

    private String readProcessOutputLimited(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] chunk = new byte[4096];
        int total = 0;
        int n;
        while ((n = in.read(chunk)) != -1) {
            if (total < maxBytes) {
                int toWrite = Math.min(n, maxBytes - total);
                buffer.write(chunk, 0, toWrite);
                total += toWrite;
            }
        }
        return buffer.toString();
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
