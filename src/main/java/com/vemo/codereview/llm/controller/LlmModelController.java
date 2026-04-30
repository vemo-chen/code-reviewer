package com.vemo.codereview.llm.controller;

import com.vemo.codereview.common.model.ApiResponse;
import com.vemo.codereview.llm.model.LlmModelDetailResponse;
import com.vemo.codereview.llm.model.LlmModelPageResponse;
import com.vemo.codereview.llm.model.LlmModelQueryRequest;
import com.vemo.codereview.llm.model.LlmModelTestResponse;
import com.vemo.codereview.llm.model.LlmModelUpsertRequest;
import com.vemo.codereview.llm.service.LlmModelService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/llm/models")
public class LlmModelController {

    private final LlmModelService llmModelService;

    public LlmModelController(LlmModelService llmModelService) {
        this.llmModelService = llmModelService;
    }

    @GetMapping
    public ApiResponse<LlmModelPageResponse> page(
        @RequestParam(defaultValue = "1") long pageNo,
        @RequestParam(defaultValue = "10") long pageSize,
        @RequestParam(required = false) String configName,
        @RequestParam(required = false) String providerCode,
        @RequestParam(required = false) String scopeType,
        @RequestParam(required = false) Boolean enabled) {
        LlmModelQueryRequest request = new LlmModelQueryRequest();
        request.setPageNo(pageNo);
        request.setPageSize(pageSize);
        request.setConfigName(configName);
        request.setProviderCode(providerCode);
        request.setScopeType(scopeType);
        request.setEnabled(enabled);
        return ApiResponse.success(llmModelService.page(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<LlmModelDetailResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(llmModelService.getById(id));
    }

    @PostMapping
    public ApiResponse<LlmModelDetailResponse> create(@RequestBody LlmModelUpsertRequest request) {
        return ApiResponse.success(llmModelService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<LlmModelDetailResponse> update(@PathVariable Long id, @RequestBody LlmModelUpsertRequest request) {
        return ApiResponse.success(llmModelService.update(id, request));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<LlmModelDetailResponse> enable(@PathVariable Long id) {
        return ApiResponse.success(llmModelService.enable(id));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<LlmModelDetailResponse> disable(@PathVariable Long id) {
        return ApiResponse.success(llmModelService.disable(id));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<LlmModelTestResponse> test(@PathVariable Long id) {
        return ApiResponse.success(llmModelService.test(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        llmModelService.delete(id);
        return ApiResponse.success(Boolean.TRUE);
    }

}
