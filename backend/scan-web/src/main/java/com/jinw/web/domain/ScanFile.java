package com.jinw.web.domain;

import lombok.Data;

import java.io.File;

@Data
public class ScanFile {

    private File file;

    private String language;
}