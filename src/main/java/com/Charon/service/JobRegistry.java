package com.Charon.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JobRegistry {
    private final Map<String, Process> processes = new ConcurrentHashMap<>();

    public void register(String jobId, Process p) {
        processes.put(jobId, p);
    }

    public Process get(String jobId) {
        return processes.get(jobId);
    }

    public void remove(String jobId) {
        processes.remove(jobId);
    }

    public boolean cancel(String jobId) {
        Process p = processes.get(jobId);
        if (p != null) {
            p.destroyForcibly();
            processes.remove(jobId);
            return true;
        }
        return false;
    }
}

