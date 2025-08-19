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
            @NotNull MultipartFile file,
            @Min(1) @Max(8) Integer gridN,
            @Min(1) @Max(120) Integer fps,
            @Pattern(regexp = "(?i)1080p|720p|4k|2160p|custom") String resolution,
            @Min(64) @Max(4096) Integer width,
            @Min(64) @Max(4096) Integer height,
            Boolean enableFec,
            @Min(0) @Max(100) Integer fecParityPercent,
            @NotBlank String passphrase,
            @NotBlank String publicKeyHint,
            @Min(0) Integer privateKeyFrameIndex,
            @NotBlank String privateKeyFramePassword,
            @Min(0) Integer obfuscationSeed,
            @RequestParam(required = false) MultipartFile obfuscationFile
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