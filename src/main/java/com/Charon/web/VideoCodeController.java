package com.Charon.web;

import com.Charon.config.AppDefaultProperties;
import com.Charon.entity.VideoRecord;
import com.Charon.repository.VideoRecordRepository;
import com.Charon.service.VideoCodeService;
import com.Charon.storage.StorageService;
import jakarta.validation.constraints.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/video-code")
@Validated
public class VideoCodeController {

    private final VideoCodeService service;
    private final VideoRecordRepository repo;
    private final StorageService storageService;
    private final AppDefaultProperties appDefaults;
    private final com.Charon.service.JobRegistry jobRegistry;

    public VideoCodeController(VideoCodeService service, VideoRecordRepository repo, StorageService storageService, AppDefaultProperties appDefaults, com.Charon.service.JobRegistry jobRegistry) {
        this.service = service;
        this.repo = repo;
        this.storageService = storageService;
        this.appDefaults = appDefaults;
        this.jobRegistry = jobRegistry;
    }

    public record EncodeRequest(
            @NotNull MultipartFile file, // 上传的原始文件 | Uploaded source file
            @Min(1) @Max(8) Integer gridN, // 每帧二维码网格尺寸 N（N×N），影响容量与纠错 | QR grid size N per frame (N×N), affects capacity and ECC
            @Min(1) @Max(120) Integer fps, // 输出视频帧率 | Output video FPS
            @Pattern(regexp = "(?i)1080p|720p|4k|2160p|custom") String resolution, // 预设分辨率或 custom | Preset resolution or custom
            @Min(64) @Max(4096) Integer width, // 自定义分辨率宽（resolution=custom 时生效） | Custom width (effective when resolution=custom)
            @Min(64) @Max(4096) Integer height, // 自定义分辨率高（resolution=custom 时生效） | Custom height (effective when resolution=custom)
            Boolean enableFec, // 是否启用前向纠错 FEC | Whether to enable forward error correction (FEC)
            @Min(0) @Max(100) Integer fecParityPercent, // FEC 冗余比例（0-100%） | FEC parity percentage (0-100%)
            @NotBlank String passphrase, // 加密口令，用于派生加密密钥 | Encryption passphrase, used to derive key
            @NotBlank String publicKeyHint, // 公钥提示（指纹/标识）用于选择公钥 | Public key hint (fingerprint/identifier) to select public key
            @Min(0) Integer privateKeyFrameIndex, // 私钥帧索引（用于解密） | Private key frame index (for decryption)
            @NotBlank String privateKeyFramePassword, // 私钥帧的保护密码（解密私钥） | Password protecting the private key frame (decrypts private key)
            @Min(0) Integer obfuscationSeed, // 混淆种子（若启用混淆） | Obfuscation seed (if enabled)
            @RequestParam(required = false) MultipartFile obfuscationFile // 混淆验证文件（可选） | Obfuscation verification file (optional)
    ) {}

    @PostMapping(value = "/encode", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> encode(@ModelAttribute @Validated EncodeRequest req) throws Exception {
        int gridN = req.gridN() == null ? appDefaults.getGridN() : req.gridN();
        int fps = req.fps() == null ? appDefaults.getFps() : req.fps();
        String resolution = req.resolution() == null ? appDefaults.getResolution() : req.resolution();
        boolean enableFec = req.enableFec() == null ? appDefaults.getEnableFec() : req.enableFec();
        Integer fecParityPercent = req.fecParityPercent() == null ? appDefaults.getFecParityPercent() : req.fecParityPercent();

        Map<String, Object> result = service.submit(
                req.file(), gridN, fps, resolution, req.width(), req.height(),
                enableFec, fecParityPercent, req.passphrase(), req.publicKeyHint(),
                req.privateKeyFrameIndex(), req.privateKeyFramePassword(), req.obfuscationSeed(), req.obfuscationFile()
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadById(@PathVariable("id") Long id,
                                                 @RequestParam(defaultValue = "video") String type,
                                                 @RequestHeader(value = "Range", required = false) String range) {
        VideoRecord vr = repo.selectById(id);
        if (vr == null) {
            return ResponseEntity.notFound().build();
        }
        String path = "manifest".equalsIgnoreCase(type) ? vr.getManifestPath() : vr.getStoragePath();
        Path p = storageService.loadAsPath(path);
        Resource res = new FileSystemResource(p);
        String filename = p.getFileName().toString();
        return buildDownloadResponse(res, p, filename, "manifest".equalsIgnoreCase(type), range);
    }

    @GetMapping("/download/by-job/{jobId}")
    public ResponseEntity<Resource> downloadByJobId(@PathVariable("jobId") String jobId,
                                                    @RequestParam(defaultValue = "video") String type,
                                                    @RequestHeader(value = "Range", required = false) String range) {
        VideoRecord vr = repo.findByJobId(jobId).orElse(null);
        if (vr == null) {
            return ResponseEntity.notFound().build();
        }
        String path = "manifest".equalsIgnoreCase(type) ? vr.getManifestPath() : vr.getStoragePath();
        Path p = storageService.loadAsPath(path);
        Resource res = new FileSystemResource(p);
        String filename = p.getFileName().toString();
        return buildDownloadResponse(res, p, filename, "manifest".equalsIgnoreCase(type), range);
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable("jobId") String jobId) {
        VideoRecord vr = repo.findByJobId(jobId).orElse(null);
        if (vr == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("jobId", jobId);
        body.put("status", vr.getStatus().name());
        body.put("id", vr.getId());
        body.put("error", vr.getErrorMessage());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/cancel/{jobId}")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable("jobId") String jobId) {
        VideoRecord vr = repo.findByJobId(jobId).orElse(null);
        if (vr == null) {
            return ResponseEntity.notFound().build();
        }
        boolean ok = jobRegistry.cancel(jobId);
        if (ok) {
            vr.setStatus(VideoRecord.ProcessStatus.FAILED);
            vr.setErrorMessage("Cancelled");
            repo.updateById(vr);
        }
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("jobId", jobId);
        body.put("cancelled", ok);
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Resource> buildDownloadResponse(Resource res, Path p, String filename, boolean isManifest, String rangeHeader) {
        try {
            long fileSize = java.nio.file.Files.size(p);
            MediaType mediaType = isManifest ? MediaType.APPLICATION_JSON : MediaType.APPLICATION_OCTET_STREAM;
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String r = rangeHeader.substring(6);
                String[] parts = r.split("-");
                long start = Long.parseLong(parts[0]);
                long end = parts.length > 1 && !parts[1].isEmpty() ? Long.parseLong(parts[1]) : (fileSize - 1);
                if (start < 0 || end >= fileSize || start > end) {
                    return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                            .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                            .build();
                }
                long length = end - start + 1;
                byte[] data;
                try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(p.toFile(), "r")) {
                    raf.seek(start);
                    data = new byte[(int) Math.min(length, 5 * 1024 * 1024)];
                    int read = raf.read(data);
                    if (read < data.length) {
                        data = java.util.Arrays.copyOf(data, read);
                    }
                }
                org.springframework.core.io.ByteArrayResource part = new org.springframework.core.io.ByteArrayResource(data);
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + (start + data.length - 1) + "/" + fileSize)
                        .contentType(mediaType)
                        .contentLength(data.length)
                        .body(part);
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentType(mediaType)
                    .contentLength(fileSize)
                    .body(res);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
