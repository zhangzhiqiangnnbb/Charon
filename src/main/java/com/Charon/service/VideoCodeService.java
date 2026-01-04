package com.Charon.service;

import com.Charon.entity.VideoRecord;
import com.Charon.repository.VideoRecordRepository;
import com.Charon.storage.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class VideoCodeService {

    @Value("${app.python.cmd:python}")
    private String pythonCmd;

    @Value("${app.ffmpeg.cmd:ffmpeg}")
    private String ffmpegCmd;

    @Value("${app.workdir:${user.home}/video-qrcode}")
    private String workdir;

    @Value("${app.jobs.maxConcurrency:2}")
    private int jobsConcurrency;

    private final VideoRecordRepository repo;
    private final StorageService storage;
    private final JobRegistry jobs;
    private ExecutorService executor;
    private static final Logger log = LoggerFactory.getLogger(VideoCodeService.class);

    public VideoCodeService(VideoRecordRepository repo, StorageService storage, JobRegistry jobs) {
        this.repo = repo;
        this.storage = storage;
        this.jobs = jobs;
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
        if (executor == null) {
            executor = Executors.newFixedThreadPool(Math.max(1, jobsConcurrency));
        }
        Path jobDir = Path.of(workdir, jobId);
        Files.createDirectories(jobDir);
        final Path jobDirFinal = jobDir;

        // 保存上传文件到临时job目录 | Save uploaded file to a temporary job directory
        Path inputZip = jobDir.resolve("input.zip");
        Files.copy(file.getInputStream(), inputZip, StandardCopyOption.REPLACE_EXISTING);
        Path obfPath = null;
        if (obfuscationFile != null && !obfuscationFile.isEmpty()) {
            obfPath = jobDir.resolve("obfuscation.bin");
            Files.copy(obfuscationFile.getInputStream(), obfPath, StandardCopyOption.REPLACE_EXISTING);
        }
        final String obfArg = obfPath != null ? obfPath.toString() : null;

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

        Map<String, Object> res = new HashMap<>();
        res.put("jobId", jobId);
        final String jid = jobId;
        final Path inputZipFinal = inputZip;
        final Integer gridNFinal = gridN;
        final Integer fpsFinal = fps;
        final String resolutionFinal = resolution;
        final Boolean enableFecFinal = enableFec;
        final Integer fecParityPercentFinal = fecParityPercent;
        final String passphraseFinal = passphrase;
        final String publicKeyHintFinal = publicKeyHint;
        final Integer privateKeyFrameIndexFinal = privateKeyFrameIndex;
        final String privateKeyFramePasswordFinal = privateKeyFramePassword;
        final Integer widthFinal = width;
        final Integer heightFinal = height;

        Runnable task = () -> {
            try {
                log.info("job start {}", jid);
                Path outputVideo = jobDirFinal.resolve("output.mp4");
                Path manifestJson = jobDirFinal.resolve("manifest.json");
                String scriptPath = Path.of("scripts", "encode_qr_video.py").toAbsolutePath().toString();
                ProcessBuilder pb = new ProcessBuilder(
                        pythonCmd,
                        scriptPath,
                        "--input", inputZipFinal.toString(),
                        "--output", outputVideo.toString(),
                        "--manifest", manifestJson.toString(),
                        "--grid", String.valueOf(gridNFinal),
                        "--fps", String.valueOf(fpsFinal),
                        "--resolution", resolutionFinal,
                        "--enable-fec", String.valueOf(enableFecFinal),
                        "--fec-ratio", String.valueOf(fecParityPercentFinal == null ? 0.2 : Math.max(0.15, Math.min(0.35, fecParityPercentFinal / 100.0))),
                        "--passphrase", passphraseFinal,
                        "--pubkey-hint", publicKeyHintFinal,
                        "--privkey-frame", privateKeyFrameIndexFinal == null ? "0" : String.valueOf(privateKeyFrameIndexFinal),
                        "--privkey-frame-pass", privateKeyFramePasswordFinal
                );
                pb.environment().put("FFMPEG_CMD", ffmpegCmd);
                if (widthFinal != null && heightFinal != null) {
                    pb.command().add("--width");
                    pb.command().add(String.valueOf(widthFinal));
                    pb.command().add("--height");
                    pb.command().add(String.valueOf(heightFinal));
                }
                if (obfArg != null) {
                    pb.command().add("--obfuscation");
                    pb.command().add(obfArg);
                }
                pb.directory(java.nio.file.Paths.get("").toAbsolutePath().toFile());
                pb.redirectErrorStream(true);

                Process p = pb.start();
                jobs.register(jid, p);
                String log = readProcessOutputLimited(p.getInputStream(), 32768);
                int code = p.waitFor();
                jobs.remove(jid);

                if (code != 0) {
                    vr.setStatus(VideoRecord.ProcessStatus.FAILED);
                    vr.setErrorMessage(log);
                    repo.updateById(vr);
                    deleteQuietly(jobDirFinal);
                    VideoCodeService.log.error("job failed {} code={}", jid, code);
                    return;
                }

                String videoStorePath;
                String manifestStorePath;
                try (InputStream vin = Files.newInputStream(outputVideo);
                     InputStream min = Files.newInputStream(manifestJson)) {
                    videoStorePath = storage.store(vin, jid + ".mp4");
                    manifestStorePath = storage.store(min, jid + "-manifest.json");
                }

                vr.setStoragePath(videoStorePath);
                vr.setManifestPath(manifestStorePath);
                vr.setOutputVideoSize(Files.size(outputVideo));
                vr.setStatus(VideoRecord.ProcessStatus.COMPLETED);
                vr.setCompletedAt(LocalDateTime.now());
                repo.updateById(vr);
                deleteQuietly(jobDirFinal);
                VideoCodeService.log.info("job done {}", jid);
            } catch (Exception e) {
                vr.setStatus(VideoRecord.ProcessStatus.FAILED);
                vr.setErrorMessage(e.getMessage());
                repo.updateById(vr);
                deleteQuietly(jobDirFinal);
                VideoCodeService.log.error("job error {} {}", jid, e.getMessage());
            }
        };

        executor.submit(task);
        return res;
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
