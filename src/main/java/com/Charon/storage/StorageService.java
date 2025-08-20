package com.Charon.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class StorageService {

    @Value("${app.sample.storage.type:local}")
    private String storageType; // local|minio (预留) | reserved for future use

    @Value("${app.sample.storage.local.base-dir:${user.home}/video-store}")
    private String baseDir;

    public String store(InputStream input, String filename) throws IOException {
        if ("local".equalsIgnoreCase(storageType)) {
            Path dir = buildTodayDir();
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            return target.toString();
        }
        throw new UnsupportedOperationException("Unsupported storage type: " + storageType);
    }

    public Path loadAsPath(String path) {
        return Paths.get(path);
    }

    private Path buildTodayDir() {
        String day = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return Paths.get(baseDir, day);
    }
}