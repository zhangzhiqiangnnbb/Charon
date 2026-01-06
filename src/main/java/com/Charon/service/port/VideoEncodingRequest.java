package com.Charon.service.port;

import java.nio.file.Path;

public record VideoEncodingRequest(
    String jobId,
    Path inputZip,
    Path outputVideo,
    Path manifestJson,
    String obfPath,
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
    String privateKeyFramePassword
) {}
