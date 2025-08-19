package com.lucas.lucasdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("video_records")
public class VideoRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("jobId")
    private String jobId;

    @TableField("originalFileName")
    private String originalFileName;

    @TableField("originalFileSize")
    private Long originalFileSize;

    @TableField("storagePath")
    private String storagePath;       // 视频文件存储路径（对象存储Key或本地路径）

    @TableField("manifestPath")
    private String manifestPath;      // 清单文件存储路径

    // 编码参数
    @TableField("gridN")
    private Integer gridN;

    @TableField("fps")
    private Integer fps;

    @TableField("resolution")
    private String resolution;

    @TableField("enableFec")
    private Boolean enableFec;

    @TableField("fecParityPercent")
    private Integer fecParityPercent;

    @TableField("publicKeyHint")
    private String publicKeyHint;

    @TableField("privateKeyFrameIndex")
    private Integer privateKeyFrameIndex;

    @TableField("obfuscationSeed")
    private Integer obfuscationSeed;

    @TableField("obfuscationFilePath")
    private String obfuscationFilePath;   // 混淆文件存储路径（如果有）

    // 状态与时间
    @TableField("status")
    private ProcessStatus status = ProcessStatus.PROCESSING;

    @TableField("errorMessage")
    private String errorMessage;

    @TableField("createdAt")
    private LocalDateTime createdAt = LocalDateTime.now();

    @TableField("completedAt")
    private LocalDateTime completedAt;

    // 输出信息
    @TableField("outputVideoSize")
    private Long outputVideoSize;

    @TableField("outputFrameCount")
    private Integer outputFrameCount;

    public enum ProcessStatus {
        PROCESSING, COMPLETED, FAILED
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public Long getOriginalFileSize() {
        return originalFileSize;
    }

    public void setOriginalFileSize(Long originalFileSize) {
        this.originalFileSize = originalFileSize;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getManifestPath() {
        return manifestPath;
    }

    public void setManifestPath(String manifestPath) {
        this.manifestPath = manifestPath;
    }

    public Integer getGridN() {
        return gridN;
    }

    public void setGridN(Integer gridN) {
        this.gridN = gridN;
    }

    public Integer getFps() {
        return fps;
    }

    public void setFps(Integer fps) {
        this.fps = fps;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public Boolean getEnableFec() {
        return enableFec;
    }

    public void setEnableFec(Boolean enableFec) {
        this.enableFec = enableFec;
    }

    public Integer getFecParityPercent() {
        return fecParityPercent;
    }

    public void setFecParityPercent(Integer fecParityPercent) {
        this.fecParityPercent = fecParityPercent;
    }

    public String getPublicKeyHint() {
        return publicKeyHint;
    }

    public void setPublicKeyHint(String publicKeyHint) {
        this.publicKeyHint = publicKeyHint;
    }

    public Integer getPrivateKeyFrameIndex() {
        return privateKeyFrameIndex;
    }

    public void setPrivateKeyFrameIndex(Integer privateKeyFrameIndex) {
        this.privateKeyFrameIndex = privateKeyFrameIndex;
    }

    public Integer getObfuscationSeed() {
        return obfuscationSeed;
    }

    public void setObfuscationSeed(Integer obfuscationSeed) {
        this.obfuscationSeed = obfuscationSeed;
    }

    public String getObfuscationFilePath() {
        return obfuscationFilePath;
    }

    public void setObfuscationFilePath(String obfuscationFilePath) {
        this.obfuscationFilePath = obfuscationFilePath;
    }

    public ProcessStatus getStatus() {
        return status;
    }

    public void setStatus(ProcessStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Long getOutputVideoSize() {
        return outputVideoSize;
    }

    public void setOutputVideoSize(Long outputVideoSize) {
        this.outputVideoSize = outputVideoSize;
    }

    public Integer getOutputFrameCount() {
        return outputFrameCount;
    }

    public void setOutputFrameCount(Integer outputFrameCount) {
        this.outputFrameCount = outputFrameCount;
    }
}