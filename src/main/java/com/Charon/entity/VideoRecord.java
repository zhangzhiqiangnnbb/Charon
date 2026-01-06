package com.Charon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("video_records")
@Data
public class VideoRecord {

    @TableId(type = IdType.AUTO)
    private Long id;                  // 主键ID，自增 | Primary key ID (auto-increment)

    @TableField("jobId")
    private String jobId;             // 任务ID（唯一作业标识） | Job ID (unique job identifier)

    @TableField("originalFileName")
    private String originalFileName;  // 原始文件名 | Original file name

    @TableField("originalFileSize")
    private Long originalFileSize;    // 原始文件大小（字节） | Original file size (bytes)

    @TableField("storagePath")
    private String storagePath;       // 视频文件存储路径（对象存储Key或本地路径） | Video file storage path (object storage key or local path)

    @TableField("manifestPath")
    private String manifestPath;      // 清单文件存储路径 | Manifest file storage path

    // 编码参数 | Encoding parameters
    @TableField("gridN")
    private Integer gridN;            // 每帧二维码网格大小 N（N×N） | QR grid size N per frame (N×N)

    @TableField("fps")
    private Integer fps;              // 输出视频帧率 | Output video FPS

    @TableField("resolution")
    private String resolution;        // 输出视频分辨率（如 1920x1080） | Output video resolution (e.g., 1920x1080)

    @TableField("enableFec")
    private Boolean enableFec;        // 是否启用前向纠错FEC | Whether forward error correction (FEC) is enabled

    @TableField("fecParityPercent")
    private Integer fecParityPercent; // FEC 冗余比例（百分比） | FEC parity percentage (%)

    @TableField("publicKeyHint")
    private String publicKeyHint;     // 公钥提示（指纹或标识） | Public key hint (fingerprint or identifier)

    @TableField("privateKeyFrameIndex")
    private Integer privateKeyFrameIndex; // 私钥帧所在索引（用于解密） | Index of private key frame (for decryption)

    @TableField("obfuscationSeed")
    private Integer obfuscationSeed;  // 混淆种子 | Obfuscation seed

    @TableField("obfuscationFilePath")
    private String obfuscationFilePath;   // 混淆文件存储路径（如果有） | Obfuscation file storage path (if any)

    @TableField("processingMode")
    private String processingMode; // 处理模式 (CPU, GPU, CLOUD)

    // 状态与时间 | Status and timestamps
    @TableField("status")
    private ProcessStatus status = ProcessStatus.PROCESSING; // 处理状态 | Processing status

    @TableField("errorMessage")
    private String errorMessage;      // 错误信息（失败原因） | Error message (failure reason)

    @TableField("createdAt")
    private LocalDateTime createdAt = LocalDateTime.now(); // 创建时间 | Created time

    @TableField("completedAt")
    private LocalDateTime completedAt; // 完成时间 | Completed time

    // 输出信息 | Output information
    @TableField("outputVideoSize")
    private Long outputVideoSize;     // 输出视频文件大小（字节） | Output video file size (bytes)

    @TableField("outputFrameCount")
    private Integer outputFrameCount; // 输出视频总帧数 | Output video total frame count

    public enum ProcessStatus {
        PROCESSING, COMPLETED, FAILED
    }

    public void complete(String storagePath, String manifestPath, Long outputVideoSize, Integer outputFrameCount) {
        this.storagePath = storagePath;
        this.manifestPath = manifestPath;
        this.outputVideoSize = outputVideoSize;
        this.outputFrameCount = outputFrameCount;
        this.status = ProcessStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = ProcessStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }
}