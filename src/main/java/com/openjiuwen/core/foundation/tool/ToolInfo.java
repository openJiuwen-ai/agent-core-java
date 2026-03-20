// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.foundation.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Tool信息描述类，供测试和外部使用的便捷入口。
 * <p>
 * 测试中使用 com.openjiuwen.core.foundation.tool.ToolInfo 导入路径。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolInfo {

    /** 工具类型，默认 function。 */
    @Builder.Default
    private String type = "function";

    /** 工具名称。 */
    @Builder.Default
    private String name = "";

    /** 工具描述。 */
    @Builder.Default
    private String description = "";

    /**
     * 参数 schema，遵循 JSON Schema 格式。
     */
    @Builder.Default
    private Map<String, Object> parameters = Map.of();

    /**
     * 三参数构造函数，供测试使用。
     *
     * @param name        工具名称
     * @param description 工具描述
     * @param parameters  参数 schema
     */
    public ToolInfo(String name, String description, Map<String, Object> parameters) {
        this.type = "function";
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }
}
