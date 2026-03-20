// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.dev_tools.tune;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定义测试用例
 * <p>
 * Mirrors Python's {@code openjiuwen.dev_tools.tune.base.Case}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Case {

    /**
     * 输入参数
     */
    private Map<String, Object> inputs;

    /**
     * 标签/期望输出
     */
    private Map<String, Object> label;

    /**
     * 工具列表
     */
    private List<ToolInfo> tools;

    /**
     * 用例ID
     */
    private String caseId;

    public Case(Map<String, ?> inputs, Map<String, ?> label) {
        this(inputs, label, null);
    }

    public Case(Map<String, ?> inputs, Map<String, ?> label, List<ToolInfo> tools) {
        this(asObjectMap(inputs), asObjectMap(label), tools, null);
    }

    private static Map<String, Object> asObjectMap(Map<String, ?> source) {
        if (source == null) {
            return null;
        }
        return new LinkedHashMap<>(source);
    }
}
