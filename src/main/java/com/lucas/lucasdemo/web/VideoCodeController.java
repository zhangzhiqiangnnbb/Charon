package com.lucas.lucasdemo.web;

import com.lucas.lucasdemo.config.AppDefaultProperties;
import com.lucas.lucasdemo.entity.VideoRecord;
import com.lucas.lucasdemo.repository.VideoRecordRepository;
import com.lucas.lucasdemo.service.VideoCodeService;
import com.lucas.lucasdemo.storage.StorageService;
import jakarta.validation.constraints.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    public VideoCodeController(VideoCodeService service, VideoRecordRepository repo, StorageService storageService, AppDefaultProperties appDefaults) {
        this.service = service;
        this.repo = repo;
        this.storageService = storageService;
        this.appDefaults = appDefaults;
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

        Map<String, Object> result = service.process(
                req.file(), gridN, fps, resolution, req.width(), req.height(),
                enableFec, fecParityPercent, req.passphrase(), req.publicKeyHint(),
                req.privateKeyFrameIndex(), req.privateKeyFramePassword(), req.obfuscationSeed(), req.obfuscationFile()
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadById(@PathVariable("id") Long id,
                                                 @RequestParam(defaultValue = "video") String type) {
        VideoRecord vr = repo.selectById(id);
        if (vr == null) {
            return ResponseEntity.notFound().build();
        }
        String path = "manifest".equalsIgnoreCase(type) ? vr.getManifestPath() : vr.getStoragePath();
        Path p = storageService.loadAsPath(path);
        Resource res = new FileSystemResource(p);
        String filename = p.getFileName().toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType("manifest".equalsIgnoreCase(type) ? MediaType.APPLICATION_JSON : MediaType.APPLICATION_OCTET_STREAM)
                .body(res);
    }

    @GetMapping("/download/by-job/{jobId}")
    public ResponseEntity<Resource> downloadByJobId(@PathVariable("jobId") String jobId,
                                                    @RequestParam(defaultValue = "video") String type) {
        VideoRecord vr = repo.findByJobId(jobId).orElse(null);
        if (vr == null) {
            return ResponseEntity.notFound().build();
        }
        String path = "manifest".equalsIgnoreCase(type) ? vr.getManifestPath() : vr.getStoragePath();
        Path p = storageService.loadAsPath(path);
        Resource res = new FileSystemResource(p);
        String filename = p.getFileName().toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType("manifest".equalsIgnoreCase(type) ? MediaType.APPLICATION_JSON : MediaType.APPLICATION_OCTET_STREAM)
                .body(res);
    }
}