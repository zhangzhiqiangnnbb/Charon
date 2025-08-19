package com.lucas.lucasdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.sample.default")
public class AppDefaultProperties {
    
    private Integer gridN = 2;
    private Integer fps = 60;
    private String resolution = "1080p";
    private Boolean enableFec = true;
    private Integer fecParityPercent = 20;

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
}