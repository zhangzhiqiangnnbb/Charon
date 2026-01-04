package com.Charon.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JobRegistry {
    private final Map<String, Process> processes = new ConcurrentHashMap<>();
    private final Map<String, Integer> progresses = new ConcurrentHashMap<>();
    private final Map<String, String> stages = new ConcurrentHashMap<>();

    public void register(String jobId, Process p) {
        processes.put(jobId, p);
    }

    public Process get(String jobId) {
        return processes.get(jobId);
    }

    public void remove(String jobId) {
        processes.remove(jobId);
        progresses.remove(jobId);
        stages.remove(jobId);
    }

    public boolean cancel(String jobId) {
        Process p = processes.get(jobId);
        if (p != null) {
            p.destroyForcibly();
            processes.remove(jobId);
            progresses.remove(jobId);
            stages.remove(jobId);
            return true;
        }
        return false;
    }

    public void setProgress(String jobId, int percent, String stage) {
        progresses.put(jobId, Math.max(0, Math.min(100, percent)));
        if (stage != null) {
            stages.put(jobId, stage);
        }
    }

    public int getProgress(String jobId) {
        return progresses.getOrDefault(jobId, 0);
    }

    public String getStage(String jobId) {
        return stages.getOrDefault(jobId, "PENDING");
    }
}
