package com.jinw.web.service;

import com.jinw.common.domain.graph.CallEdge;
import com.jinw.web.domain.*;
import com.jinw.web.domain.response.AsmInfoResponse;
import com.jinw.web.domain.response.FrameworkPortraitResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface WebService {
    String startScan(MultipartFile file, String appId, String scanType);

    List<BTAppInfo> scanQueueList();

    List<BTQuestionInfo> questionList(String taskId, String isAdapt);

    ScanProgress progress(String taskId);

    FrameworkPortraitResponse frameworkPortrait(String taskId);

    List<BTLib> libList(String taskId);

    AsmInfoResponse asmList(String taskId);

    List<DirectoryNodeDTO> listTree(String taskId, String parentPath, String isAdapt);

    String getFileText(String taskId, String filePath);

    List<BTQuestionInfo> getFileQuestions(String filePath, String taskId, String isAdapt);

    List<CallEdge> getProjectGraph(String taskId);

    List<BTQuestionInfo> getMakeFileInfo(String taskId);

    ReportData getReportInfo(String id);

    void downloadReport(String id, String status, HttpServletResponse response) throws IOException;
}
