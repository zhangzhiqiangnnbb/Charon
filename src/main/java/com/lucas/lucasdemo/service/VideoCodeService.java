package com.lucas.lucasdemo.service;

import com.lucas.lucasdemo.entity.VideoRecord;
import com.lucas.lucasdemo.repository.VideoRecordRepository;
import com.lucas.lucasdemo.storage.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
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

    public VideoCodeService(VideoRecordRepository repo, StorageService storage) {
        this.repo = repo;
        this.storage = storage;
    }

    public Map<String, Object> process(MultipartFile file,
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
                                       MultipartFile obfuscationFile) throws IOException, InterruptedException {
        String jobId = UUID.randomUUID().toString();
        Path jobDir = Path.of(workdir, jobId);
        Files.createDirectories(jobDir);

        // 保存上传文件到临时job目录
        Path inputZip = jobDir.resolve("input.zip");
        Files.copy(file.getInputStream(), inputZip, StandardCopyOption.REPLACE_EXISTING);
        Path obfPath = null;
        if (obfuscationFile != null && !obfuscationFile.isEmpty()) {
            obfPath = jobDir.resolve("obfuscation.bin");
            Files.copy(obfuscationFile.getInputStream(), obfPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // 预建数据库记录（PROCESSING）
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

        // 调用python生成视频
        Path outputVideo = jobDir.resolve("output.mp4");
        Path manifestJson = jobDir.resolve("manifest.json");
        ProcessBuilder pb = new ProcessBuilder(
                pythonCmd,
                Path.of("scripts", "encode_qr_video.py").toString(),
                "--input", inputZip.toString(),
                "--output", outputVideo.toString(),
                "--manifest", manifestJson.toString(),
                "--grid", String.valueOf(gridN),
                "--fps", String.valueOf(fps),
                "--resolution", resolution,
                "--enable-fec", String.valueOf(enableFec),
                // 转换百分比为浮点数比例（20% -> 0.2），限制在0.15-0.35之间
                "--fec-ratio", String.valueOf(fecParityPercent == null ? 0.2 : Math.max(0.15, Math.min(0.35, fecParityPercent / 100.0))),
                "--passphrase", passphrase,
                "--pubkey-hint", publicKeyHint,
                "--privkey-frame", privateKeyFrameIndex == null ? "0" : String.valueOf(privateKeyFrameIndex),
                "--privkey-frame-pass", privateKeyFramePassword
        );
        if (width != null && height != null) {
            pb.command().add("--width");
            pb.command().add(String.valueOf(width));
            pb.command().add("--height");
            pb.command().add(String.valueOf(height));
        }
        if (obfPath != null) {
            pb.command().add("--obfuscation");
            pb.command().add(obfPath.toString());
        }
        pb.directory(new File("c:" + File.separator + "demo" + File.separator + "lucasdemo"));
        pb.redirectErrorStream(true);

        Process p = pb.start();
        String log = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();

        Map<String, Object> res = new HashMap<>();
        res.put("jobId", jobId);
        res.put("exitCode", code);
        res.put("log", log);

        if (code != 0) {
            vr.setStatus(VideoRecord.ProcessStatus.FAILED);
            vr.setErrorMessage(log);
            repo.updateById(vr);
            res.put("error", "Encoding failed");
            return res;
        }

        // 将结果文件落地到对象存储（本地）
        String videoStorePath;
        String manifestStorePath;
        try (InputStream vin = Files.newInputStream(outputVideo);
             InputStream min = Files.newInputStream(manifestJson)) {
            videoStorePath = storage.store(vin, jobId + ".mp4");
            manifestStorePath = storage.store(min, jobId + "-manifest.json");
        }

        vr.setStoragePath(videoStorePath);
        vr.setManifestPath(manifestStorePath);
        vr.setOutputVideoSize(Files.size(outputVideo));
        vr.setStatus(VideoRecord.ProcessStatus.COMPLETED);
        vr.setCompletedAt(LocalDateTime.now());
        repo.updateById(vr);

        res.put("videoPath", videoStorePath);
        res.put("manifestPath", manifestStorePath);
        res.put("id", vr.getId());
        return res;
    }
}