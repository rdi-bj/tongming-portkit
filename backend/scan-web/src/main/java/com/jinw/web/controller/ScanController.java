package com.jinw.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.jinw.common.domain.graph.CallEdge;
import com.jinw.web.base.RestResult;
import com.jinw.web.domain.*;
import com.jinw.web.domain.response.AsmInfoResponse;
import com.jinw.web.domain.response.FrameworkPortraitResponse;
import com.jinw.web.service.BTAppInfoService;
import com.jinw.web.service.WebService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/cscan")
@Tag(name = "移植检测接口")
public class ScanController {

    private final WebService webService;

    private final BTAppInfoService btAppInfoService;

    public ScanController(WebService webService, BTAppInfoService btAppInfoService) {
        this.webService = webService;
        this.btAppInfoService = btAppInfoService;
    }

    @PostMapping("/create")
    @Operation(summary = "新建检测项目")
    @ApiOperationSupport(order = 10)
    public RestResult<String> create(@RequestBody BTAppInfo appInfo) {
        String appId = btAppInfoService.create(appInfo);
        return RestResult.success(appId);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "删除检测项目",
            parameters = {
                    @Parameter(
                            name = "id",
                            description = "项目ID",
                            required = true
                    )
            })
    @ApiOperationSupport(order = 20)
    public RestResult<String> delete(@PathVariable String id) {
        return RestResult.success(btAppInfoService.deleteById(id));
    }

    @PutMapping("/update")
    @Operation(summary = "更新检测项目")
    @ApiOperationSupport(order = 30)
    public RestResult<String> update(@RequestBody BTAppInfo appInfo) {
        return RestResult.success(btAppInfoService.update(appInfo));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "获取检测项目详情",
            parameters = {
                    @Parameter(
                            name = "id",
                            description = "项目ID",
                            required = true
                    )
            })
    @ApiOperationSupport(order = 40)
    public RestResult<BTAppInfo> get(@PathVariable String id) {
        return RestResult.success(btAppInfoService.getById(id));
    }

    @GetMapping("/page")
    @Operation(summary = "获取检测项目列表")
    @ApiOperationSupport(order = 50)
    public RestResult<Page<BTAppInfo>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword
    ) {

        Page<BTAppInfo> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<BTAppInfo> wrapper = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w
                    .like(BTAppInfo::getName, keyword)
                    .or()
                    .like(BTAppInfo::getDescription, keyword)
            );
        }

        wrapper.orderByDesc(BTAppInfo::getCreateTime);

        return RestResult.success(btAppInfoService.page(page, wrapper));
    }

    @PostMapping("/startScan")
    @Operation(
            summary = "启动检测任务",
            parameters = {
                    @Parameter(
                            name = "file",
                            description = "评估文件",
                            required = true
                    ),
                    @Parameter(
                            name = "appId",
                            description = "项目ID",
                            required = true
                    ),
                    @Parameter(
                            name = "scanType",
                            description = "扫描类型 0-快速 1-深度",
                            required = false
                    )
            })
    @ApiOperationSupport(order = 60)
    public RestResult<String> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("appId") String appId,
            @RequestParam(name = "scanType",defaultValue = "0",required = false) String scanType) {
        return RestResult.success(webService.startScan(file, appId,scanType));
    }

    @GetMapping("/scanQueueList")
    @Operation(summary = "获取正在排队和扫描的项目")
    @ApiOperationSupport(order = 65)
    public RestResult<List<BTAppInfo>> scanQueueList() {
        return RestResult.success(webService.scanQueueList());
    }

    @GetMapping("/progress")
    @Operation(
            summary = "获取检测进度",
            parameters = {
                    @Parameter(
                            name = "taskId",
                            description = "任务ID",
                            required = true
                    )
            })
    @ApiOperationSupport(order = 70)
    public RestResult<ScanProgress> progress(@RequestParam("taskId") String taskId) {
        return RestResult.success(webService.progress(taskId));
    }

    @GetMapping("/questionList")
    @Operation(
            summary = "获取检测结果",
            parameters = {
                    @Parameter(
                            name = "taskId",
                            description = "任务ID",
                            required = true
                    ),
                    @Parameter(
                            name = "isAdapt",
                            description = "是否是适配查询",
                            required = true
                    )
            })
    @ApiOperationSupport(order = 80)
    public RestResult<List<BTQuestionInfo>> questionList(@RequestParam("taskId") String taskId,
                                                         @RequestParam("isAdapt") String isAdapt) {
        return RestResult.success(webService.questionList(taskId,isAdapt));
    }

    @GetMapping("/frameworkPortrait")
    @Operation(
            summary = "获取检测画像",
            parameters = {
                    @Parameter(
                            name = "taskId",
                            description = "任务ID",
                            required = true
                    )
            })
    @ApiOperationSupport(order = 90)
    public RestResult<FrameworkPortraitResponse> frameworkPortrait(@RequestParam("taskId") String taskId) {
        return RestResult.success(webService.frameworkPortrait(taskId));
    }

    @GetMapping("/libList")
    @Operation(
            summary = "获取检测到的包信息",
            parameters = {
                    @Parameter(
                            name = "taskId",
                            description = "任务ID",
                            required = true
                    )
            })
    @ApiOperationSupport(order = 100)
    public RestResult<List<BTLib>> libList(@RequestParam("taskId") String taskId) {
        return RestResult.success(webService.libList(taskId));
    }

    @GetMapping("/asmList")
    @Operation(
            summary = "获取检测到的汇编信息",
            parameters = {
                    @Parameter(
                            name = "taskId",
                            description = "任务ID",
                            required = true
                    )
            })
    @ApiOperationSupport(order = 110)
    public RestResult<AsmInfoResponse> asmList(@RequestParam("taskId") String taskId) {
        return RestResult.success(webService.asmList(taskId));
    }

    @GetMapping("/{taskId}/tree")
    @Operation(
            summary = "获取目录树",
            parameters = {
                    @Parameter(
                            name = "taskId",
                            description = "任务ID",
                            required = true
                    ),
                    @Parameter(
                            name = "parentPath",
                            description = "父目录，为空表示查询根目录",
                            required = false
                    ),
                    @Parameter(
                            name = "isAdapt",
                            description = "是否是适配查询",
                            required = true
                    )
            }
    )
    public RestResult<List<DirectoryNodeDTO>> listTree(
            @PathVariable String taskId,
            @RequestParam(required = false) String parentPath,
            @RequestParam("isAdapt") String isAdapt) {

        return RestResult.success(webService.listTree(taskId, parentPath, isAdapt));
    }

    @GetMapping("/getFileText")
    @Operation(
            summary = "获取文件内容",
            parameters = {
                    @Parameter(
                            name = "taskId",
                            description = "任务ID",
                            required = true
                    ),
                    @Parameter(
                            name = "filePath",
                            description = "问题文件相对路径，例子：proot-5.4.0/src/arch.h",
                            required = true
                    )
            }
    )
    public RestResult<String> getFileText(
            @RequestParam("taskId") String taskId,
            @RequestParam("filePath") String filePath) {
        return RestResult.success(webService.getFileText(taskId, filePath));
    }

    @GetMapping("/getFileQuestions")
    @Operation(
            summary = "获取文件内容",
            parameters = {
                    @Parameter(
                            name = "taskId",
                            description = "任务ID",
                            required = true
                    ),
                    @Parameter(
                            name = "filePath",
                            description = "问题文件相对路径，例子：proot-5.4.0/src/arch.h",
                            required = true
                    ),
                    @Parameter(
                            name = "isAdapt",
                            description = "是否是适配查询 1-是 0-否",
                            required = false
                    )
            }
    )
    public RestResult<List<BTQuestionInfo>> getFileQuestions(@RequestParam("filePath") String filePath,
                                                             @RequestParam("taskId") String taskId,
                                                             @RequestParam(value = "isAdapt", required = false) String isAdapt) {
        return RestResult.success(webService.getFileQuestions(filePath, taskId, isAdapt));
    }

    @GetMapping("/getProjectGraph")
    @Operation(
            summary = "获取项目关系",
            parameters = {
                    @Parameter(
                            name = "taskId",
                            description = "任务ID",
                            required = true
                    )
            }
    )
    public RestResult<List<CallEdge>> getProjectGraph(@RequestParam("taskId") String taskId) {
        return RestResult.success(webService.getProjectGraph(taskId));
    }

    @GetMapping("/getMakeFileInfo")
    @Operation(
            summary = "获取构造文件信息",
            parameters = {
                    @Parameter(
                            name = "taskId",
                            description = "任务ID",
                            required = true
                    )
            }
    )
    public RestResult<List<BTQuestionInfo>> getMakeFileInfo(@RequestParam("taskId") String taskId) {
        return RestResult.success(webService.getMakeFileInfo(taskId));
    }

    @GetMapping("/getReportInfo")
    @Operation(
            summary = "获取报告内容",
            parameters = {
                    @Parameter(
                            name = "id",
                            description = "项目ID",
                            required = true
                    )
            }
    )
    public RestResult<ReportData> getReportInfo(@RequestParam("id") String id) {
        return RestResult.success(webService.getReportInfo(id));
    }

    @GetMapping("/downloadReport")
    @Operation(
            summary = "下载报告",
            parameters = {
                    @Parameter(
                            name = "id",
                            description = "项目ID",
                            required = true
                    ),
                    @Parameter(
                            name = "status",
                            description = "是否AI适配后生成报告 1:是 0:不是",
                            required = true
                    )
            }
    )
    public void downloadReport(@RequestParam("id") String id,
                               @RequestParam("status") String status,
                               HttpServletResponse response) throws IOException {
        webService.downloadReport(id, status, response);
    }
}
