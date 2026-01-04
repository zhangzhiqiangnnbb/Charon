# Charon

一个基于 Spring Boot 的“文件 ↔ 二维码视频”编码/解码服务示例工程：上传文件后通过 Python + FFmpeg 将数据编码为视频（以及 manifest），并提供任务状态查询与下载接口。

## 功能

- 文件编码为二维码视频（支持 FEC、参数可配置）
- 任务状态查询 / 取消任务
- 按 `id` 或 `jobId` 下载视频与 manifest
- 默认 H2 内存库快速试跑，可切换 MySQL（已包含 Flyway 迁移脚本）
- 本地文件系统存储（预留扩展）

## 环境要求

- JDK 24（项目 `pom.xml` 里配置了 `java.version=24`）
- Python 3（用于执行 `scripts/*.py`）
- FFmpeg（Windows 默认使用 `tools/ffmpeg.exe`；其他系统建议配置为 `ffmpeg` 并确保在 PATH）

安装 Python 依赖：

```bash
python -m pip install -r scripts/requirements.txt
```

## 快速开始

启动服务：

```bash
./mvnw spring-boot:run
```

默认端口：`http://localhost:8080`

## 配置

核心配置位于 [application.properties](file:///c:/work/project/Charon/src/main/resources/application.properties)：

- `app.python.cmd`：Python 可执行文件（默认 `python`）
- `app.workdir`：工作目录（帧/视频/临时文件）
- `app.ffmpeg.cmd`：FFmpeg 可执行文件（默认 `tools/ffmpeg.exe`）
- `app.storage.local.base-dir`：对象存储落地目录（默认 `${user.home}/video-store`）
- `app.jobs.maxConcurrency`：最大并发任务数

如需切换 MySQL，可参考 [application-mysql.properties](file:///c:/work/project/Charon/src/main/resources/application-mysql.properties)。

## API

接口定义见 [VideoCodeController](file:///c:/work/project/Charon/src/main/java/com/Charon/web/VideoCodeController.java)。

### 编码上传

`POST /api/video-code/encode`（`multipart/form-data`）

必填字段：

- `file`
- `passphrase`
- `publicKeyHint`
- `privateKeyFramePassword`

示例：

```bash
curl -X POST "http://localhost:8080/api/video-code/encode" \
  -F "file=@test.zip" \
  -F "gridN=2" \
  -F "fps=60" \
  -F "resolution=1080p" \
  -F "enableFec=true" \
  -F "fecParityPercent=20" \
  -F "passphrase=secret123" \
  -F "publicKeyHint=demo-key" \
  -F "privateKeyFramePassword=frame-secret"
```

### 状态查询

`GET /api/video-code/status/{jobId}`

### 取消任务

`POST /api/video-code/cancel/{jobId}`

### 下载

- `GET /api/video-code/download/{id}?type=video|manifest`
- `GET /api/video-code/download/by-job/{jobId}?type=video|manifest`

## License

Apache License 2.0，见 [LICENSE](file:///c:/work/project/Charon/LICENSE)。

