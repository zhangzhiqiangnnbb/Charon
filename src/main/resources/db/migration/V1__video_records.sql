CREATE TABLE IF NOT EXISTS video_records (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  jobId VARCHAR(64) NOT NULL,
  originalFileName VARCHAR(255),
  originalFileSize BIGINT,
  storagePath VARCHAR(1024),
  manifestPath VARCHAR(1024),
  gridN INT,
  fps INT,
  resolution VARCHAR(64),
  enableFec BOOLEAN,
  fecParityPercent INT,
  publicKeyHint VARCHAR(255),
  privateKeyFrameIndex INT,
  obfuscationSeed INT,
  obfuscationFilePath VARCHAR(1024),
  status VARCHAR(16),
  errorMessage VARCHAR(2048),
  createdAt TIMESTAMP,
  completedAt TIMESTAMP,
  outputVideoSize BIGINT,
  outputFrameCount INT
);

CREATE INDEX IF NOT EXISTS idx_video_records_jobId ON video_records(jobId);
CREATE INDEX IF NOT EXISTS idx_video_records_createdAt ON video_records(createdAt);
