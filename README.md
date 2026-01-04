# Charon

A Spring Boot sample service for “file ↔ QR-code video” encoding/decoding. Upload a file, then the service uses Python + FFmpeg to encode data into a video (and a manifest), and provides job status and download APIs.

## Features

- Encode files into QR-code videos (FEC supported, configurable parameters)
- Job status query / job cancellation
- Download video and manifest by `id` or `jobId`
- H2 in-memory DB by default for quick start; can switch to MySQL (Flyway migration included)
- Local filesystem storage (designed to be extensible)

## Requirements

- JDK 24 (`pom.xml` sets `java.version=24`)
- Python 3 (to run `scripts/*.py`)
- FFmpeg (Windows defaults to `tools/ffmpeg.exe`; on other OSes, set to `ffmpeg` and ensure it is on PATH)

Install Python dependencies:

```bash
python -m pip install -r scripts/requirements.txt
```

## Quick Start

Start the service:

```bash
./mvnw spring-boot:run
```

Default base URL: `http://localhost:8080`

## Configuration

Core config is in [application.properties](file:///c:/work/project/Charon/src/main/resources/application.properties):

- `app.python.cmd`: Python executable (default `python`)
- `app.workdir`: Working directory (frames/video/temp files)
- `app.ffmpeg.cmd`: FFmpeg executable (default `tools/ffmpeg.exe`)
- `app.storage.local.base-dir`: Storage directory (default `${user.home}/video-store`)
- `app.jobs.maxConcurrency`: Max concurrent jobs

To switch to MySQL, see [application-mysql.properties](file:///c:/work/project/Charon/src/main/resources/application-mysql.properties).

## API

API definitions are in [VideoCodeController](file:///c:/work/project/Charon/src/main/java/com/Charon/web/VideoCodeController.java).

### Encode (Upload)

`POST /api/video-code/encode` (`multipart/form-data`)

Required fields:

- `file`
- `passphrase`
- `publicKeyHint`
- `privateKeyFramePassword`

Example:

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

### Job Status

`GET /api/video-code/status/{jobId}`

### Cancel Job

`POST /api/video-code/cancel/{jobId}`

### Download

- `GET /api/video-code/download/{id}?type=video|manifest`
- `GET /api/video-code/download/by-job/{jobId}?type=video|manifest`

## License

Apache License 2.0. See [LICENSE](file:///c:/work/project/Charon/LICENSE).

