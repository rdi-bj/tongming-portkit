package com.jinw.common.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FileCategoryStat {

    private static final int MAX_FILE_PATH = 10;

    private int fileCount;

    private long codeLines;

    private long commentLines;

    private long blankLines;

    private long totalLines;

    private List<String> filePath = new ArrayList<>();

    public void addFilePath(String path) {
        if (filePath.size() < MAX_FILE_PATH) {
            filePath.add(path);
        }
    }
}