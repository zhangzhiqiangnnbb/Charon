package com.Charon.dto;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class JobMessage {
    private String jobId;
    private String jobDirPath;
    private String inputZipPath;
    private String obfArg;
    private int gridN;
    private int fps;
    private String resolution;
    private boolean enableFec;
    private Integer fecParityPercent;
    private String passphrase;
    private String publicKeyHint;
    private Integer privateKeyFrameIndex;
    private String privateKeyFramePassword;
    private Integer width;
    private Integer height;
    private String processingMode;
}
