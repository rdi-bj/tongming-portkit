package com.jinw.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.jinw.web.base.RestResult;
import com.jinw.web.domain.BTAppInfo;
import com.jinw.web.domain.BTLlmConfig;
import com.jinw.web.domain.BTLlmResult;
import com.jinw.web.domain.LlmQuestionResult;
import com.jinw.web.service.BTAppInfoService;
import com.jinw.web.service.BTLlmConfigService;
import com.jinw.web.service.impl.statemachine.ScanState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/llm")
@Tag(name = "大模型相关接口")
public class LlmController {

    private final BTLlmConfigService btLlmConfigService;
    private final BTAppInfoService bTAppInfoService;

    public LlmController(BTLlmConfigService btLlmConfigService, BTAppInfoService bTAppInfoService) {
        this.btLlmConfigService = btLlmConfigService;
        this.bTAppInfoService = bTAppInfoService;
    }

    @PostMapping("/create")
    @Operation(summary = "新建大模型配置")
    @ApiOperationSupport(order = 10)
    public RestResult<String> create(@RequestBody BTLlmConfig btLlmConfig) {
        String appId = btLlmConfigService.create(btLlmConfig);
        return RestResult.success(appId);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "删除大模型配置",
            parameters = {
                    @Parameter(
                            name = "id",
                            description = "大模型配置ID",
                            required = true
                    )
            })
    @ApiOperationSupport(order = 20)
    public RestResult<String> delete(@PathVariable String id) {
        return RestResult.success(btLlmConfigService.deleteById(id));
    }

    @PutMapping("/update")
    @Operation(summary = "更新大模型配置")
    @ApiOperationSupport(order = 30)
    public RestResult<String> update(@RequestBody BTLlmConfig btLlmConfig) {
        return RestResult.success(btLlmConfigService.update(btLlmConfig));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "获取大模型配置详情",
            parameters = {
                    @Parameter(
                            name = "id",
                            description = "大模型配置ID",
                            required = true
                    )
            })
    @ApiOperationSupport(order = 40)
    public RestResult<BTLlmConfig> get(@PathVariable String id) {
        return RestResult.success(btLlmConfigService.getById(id));
    }

    @GetMapping("/page")
    @Operation(summary = "获取大模型配置列表")
    @ApiOperationSupport(order = 50)
    public RestResult<Page<BTLlmConfig>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword
    ) {

        Page<BTLlmConfig> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<BTLlmConfig> wrapper = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w
                    .like(BTLlmConfig::getRemark, keyword)
                    .or()
                    .like(BTLlmConfig::getConfigName, keyword)
            );
        }

        wrapper.orderByDesc(BTLlmConfig::getCreateTime);

        return RestResult.success(btLlmConfigService.page(page, wrapper));
    }

    @GetMapping("/pageList")
    @Operation(summary = "获取可适配检测项目列表")
    @ApiOperationSupport(order = 60)
    public RestResult<Page<BTAppInfo>> pageList(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword
    ) {

        Page<BTAppInfo> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<BTAppInfo> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(BTAppInfo::getStatus, ScanState.SUCCESS);

        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w
                    .like(BTAppInfo::getName, keyword)
                    .or()
                    .like(BTAppInfo::getDescription, keyword)
            );
        }

        wrapper.orderByDesc(BTAppInfo::getCreateTime);

        return RestResult.success(bTAppInfoService.page(page, wrapper));
    }

    @PostMapping("/adaptFile")
    @Operation(
            summary = "单个文件适配",
            parameters = {
                    @Parameter(
                            name = "fileId",
                            description = "文件ID",
                            required = true
                    )
            })
    @ApiOperationSupport(order = 70)
    public RestResult<String> adaptFile(
            @RequestParam("fileId") String fileId) {
        return RestResult.success(btLlmConfigService.adaptFile(fileId));
    }

    @GetMapping("/fileAdaptResult")
    @Operation(
            summary = "单个文件适配结果",
            parameters = {
                    @Parameter(
                            name = "fileId",
                            description = "文件ID",
                            required = true
                    )
            })
    @ApiOperationSupport(order = 80)
    public RestResult<LlmQuestionResult> fileAdaptResult(
            @RequestParam("fileId") String fileId) {
        return RestResult.success(btLlmConfigService.fileAdaptResult(fileId));
    }

    @PostMapping("/changeLlmConfig")
    @Operation(
            summary = "切换大模型配置",
            parameters = {
                    @Parameter(
                            name = "id",
                            description = "大模型ID",
                            required = true
                    )
            })
    @ApiOperationSupport(order = 90)
    public RestResult<String> changeLlmConfig(
            @RequestParam("id") String id) {
        return RestResult.success(btLlmConfigService.changeLlmConfig(id));
    }

    @PostMapping("/adaptProject")
    @Operation(
            summary = "批量文件适配",
            parameters = {
                    @Parameter(
                            name = "taskId",
                            description = "任务ID",
                            required = true
                    )
            })
    @ApiOperationSupport(order = 100)
    public RestResult<String> adaptProject(
            @RequestParam("taskId") String taskId) {
        return RestResult.success(btLlmConfigService.adaptProject(taskId));
    }
}
