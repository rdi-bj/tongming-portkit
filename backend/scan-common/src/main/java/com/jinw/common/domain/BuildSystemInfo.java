package com.jinw.common.domain;

import com.jinw.common.enums.BuildSystem;

import java.nio.file.Path;

public class BuildSystemInfo {

    public final BuildSystem system;
    public final int confidence;   // 0–100
    public final Path file;

    public BuildSystemInfo(BuildSystem system, int confidence, Path file) {
        this.system = system;
        this.confidence = confidence;
        this.file = file;
    }

    @Override
    public String toString() {
        return system + " (confidence=" + confidence + "%, file=" + file.getFileName() + ")";
    }
}