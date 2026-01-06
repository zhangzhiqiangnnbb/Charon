package com.Charon.service.command;

import org.springframework.web.multipart.MultipartFile;

public record SubmitJobCommand(
    MultipartFile file,
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
    MultipartFile obfuscationFile
) {}
