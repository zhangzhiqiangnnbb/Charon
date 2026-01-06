package com.Charon.infrastructure.encoder;

import com.Charon.service.JobRegistry;
import com.Charon.service.port.VideoEncoder;
import com.Charon.service.port.VideoEncodingRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class PythonScriptVideoEncoder implements VideoEncoder {

    private final JobRegistry jobRegistry;
    private final String pythonCmd;
    private final String ffmpegCmd;

    public PythonScriptVideoEncoder(JobRegistry jobRegistry,
                                    @Value("${app.python.cmd:python}") String pythonCmd,
                                    @Value("${app.ffmpeg.cmd:ffmpeg}") String ffmpegCmd) {
        this.jobRegistry = jobRegistry;
        this.pythonCmd = pythonCmd;
        this.ffmpegCmd = ffmpegCmd;
    }

    @Override
    public String encode(VideoEncodingRequest request) throws Exception {
        String scriptPath = Path.of("scripts", "encode_qr_video.py").toAbsolutePath().toString();
        
        List<String> cmd = new ArrayList<>();
        cmd.add(pythonCmd);
        cmd.add(scriptPath);
        cmd.add("--input"); cmd.add(request.inputZip().toString());
        cmd.add("--output"); cmd.add(request.outputVideo().toString());
        cmd.add("--manifest"); cmd.add(request.manifestJson().toString());
        cmd.add("--grid"); cmd.add(String.valueOf(request.gridN()));
        cmd.add("--fps"); cmd.add(String.valueOf(request.fps()));
        cmd.add("--resolution"); cmd.add(request.resolution());
        cmd.add("--enable-fec"); cmd.add(String.valueOf(request.enableFec()));
        
        double fecRatio = request.fecParityPercent() == null ? 0.2 : 
                          Math.max(0.15, Math.min(0.35, request.fecParityPercent() / 100.0));
        cmd.add("--fec-ratio"); cmd.add(String.valueOf(fecRatio));
        
        cmd.add("--passphrase"); cmd.add(request.passphrase());
        cmd.add("--pubkey-hint"); cmd.add(request.publicKeyHint());
        cmd.add("--privkey-frame"); cmd.add(request.privateKeyFrameIndex() == null ? "0" : String.valueOf(request.privateKeyFrameIndex()));
        
        if (request.privateKeyFramePassword() != null) {
            cmd.add("--privkey-frame-pass"); cmd.add(request.privateKeyFramePassword());
        }

        if (request.width() != null && request.height() != null) {
            cmd.add("--width"); cmd.add(String.valueOf(request.width()));
            cmd.add("--height"); cmd.add(String.valueOf(request.height()));
        }
        
        if (request.obfPath() != null) {
            cmd.add("--obfuscation"); cmd.add(request.obfPath());
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("FFMPEG_CMD", ffmpegCmd);
        pb.directory(java.nio.file.Paths.get("").toAbsolutePath().toFile());
        pb.redirectErrorStream(true);

        Process p = pb.start();
        jobRegistry.register(request.jobId(), p);
        jobRegistry.setProgress(request.jobId(), 20, "ENCODING");
        
        String logStr = readProcessOutputLimited(p.getInputStream(), 32768);
        int code = p.waitFor();
        jobRegistry.remove(request.jobId());

        if (code != 0) {
            throw new RuntimeException("Encoding failed with code " + code + ": " + logStr);
        }
        
        return logStr;
    }

    private String readProcessOutputLimited(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] chunk = new byte[4096];
        int total = 0;
        int n;
        while ((n = in.read(chunk)) != -1) {
            if (total < maxBytes) {
                int toWrite = Math.min(n, maxBytes - total);
                buffer.write(chunk, 0, toWrite);
                total += toWrite;
            }
        }
        return buffer.toString();
    }
}
