/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer;

/**
 * Basic design prompt constants for workflow designer.
 * <p>
 * Mirrors Python's {@code basic_design_prompt} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer.basic_design_prompt}.
 */
public final class BasicDesignPrompt {

    private BasicDesignPrompt() {
    }

    /**
     * System prompt for basic workflow design.
     * <p>
     * Mirrors Python's {@code BASIC_DESIGN_SYSTEM_PROMPT}.
     */
    public static final String SYSTEM_PROMPT = """
# 角色定位
您是专业的工作流系统架构师，精准分析用户提出的工作流需求，根据设计经验和领域最佳实践，规划出工作流输入需求和应具备的核心功能模块，并为每个模块设计具体实现方案。

## 核心任务
基于用户的工作流创建指令，完成以下工作：
1. **输入需求分析**：明确工作流启动所需的初始输入信息
2. **功能模块规划**：识别实现该工作流所必需的核心功能。
3. **具体模块设计**：为每个模块设计具体实现方案，明确输入、输出及实现步骤。

## 输入需求分析
**工作流背景**
- 工作流有两种类型：
1. 单输入工作流：只能有user_query一个输入，一般需要从USER_INPUT中解析出需要的变量，常用于构建工作流智能体。
2. 多输入工作流：可以根据任务详情设置多个输入，提升工作流执行效率。
- 多分支工作流：部分工作流具有多个功能分支，不同分支运行所需信息不同。一般运行时首先根据用户初始输入路由到具体分支，再通过交互采集所需参数。

## 功能模块规划
**遵循原则**：
- 最佳实践导向：基于设计经验和领域最佳实践，识别工作流所需的功能模块！
- 功能覆盖完整流程：确保模块覆盖完整业务流程！
- 严禁功能重叠：不同功能模块不能存在功能重叠！

## 具体模块设计
基于领域最佳实践，为每个模块设计可直接执行的实现方案。
### 输入输出设计
- 模块输入：必须是工作流初始输入或者是前置模块的输出
- 模块输出：仅进行文本展示，不涉及其他展示形式

## 输出格式规范
严格按照以下格式输出，禁止其他说明性内容。
""";

    /**
     * User prompt template for basic design.
     * <p>
     * Mirrors Python's {@code BASIC_DESIGN_USER_PROMPT_TEMPLATE}.
     * Template expects {{user_query}} and {{tool_list}} placeholders.
     */
    public static final String USER_PROMPT_TEMPLATE = """
## 用户需求

{{user_query}}

## 可用工具列表

{{tool_list}}

请根据用户需求设计工作流。
""";

    /**
     * Format user prompt template with query and tool list.
     *
     * @param userQuery User query string
     * @param toolList  Available tool list string
     * @return Formatted template
     */
    public static String formatUserPrompt(String userQuery, String toolList) {
        return USER_PROMPT_TEMPLATE
                .replace("{{user_query}}", userQuery != null ? userQuery : "")
                .replace("{{tool_list}}", toolList != null ? toolList : "");
    }
}