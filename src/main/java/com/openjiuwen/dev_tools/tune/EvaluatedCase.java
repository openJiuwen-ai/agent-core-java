// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.dev_tools.tune;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 定义评估后的测试用例
 * <p>
 * Mirrors Python's {@code openjiuwen.dev_tools.tune.base.EvaluatedCase}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluatedCase {

    /**
     * 原始测试用例
     */
    private Case case_;

    /**
     * 模型回答
     */
    private Map<String, Object> answer;

    /**
     * 评分（0.0-1.0）
     */
    private float score;

    /**
     * 评分原因
     */
    private String reason;

    /**
     * 获取输入参数
     *
     * @return 输入参数
     */
    public Map<String, Object> getInputs() {
        return case_ != null ? case_.getInputs() : null;
    }

    /**
     * 获取标签/期望输出
     *
     * @return 标签
     */
    public Map<String, Object> getLabel() {
        return case_ != null ? case_.getLabel() : null;
    }

    /**
     * 获取工具列表
     *
     * @return 工具列表
     */
    public java.util.List<com.openjiuwen.core.foundation.tool.schema.ToolInfo> getTools() {
        return case_ != null ? case_.getTools() : null;
    }

    /**
     * 获取用例ID
     *
     * @return 用例ID
     */
    public String getCaseId() {
        return case_ != null ? case_.getCaseId() : null;
    }
}