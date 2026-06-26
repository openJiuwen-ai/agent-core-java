/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DL generator for workflow definition language.
 *
 * <p>Mirrors Python's {@code DLGenerator} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_generator.py}.</p>
 */
public class DLGenerator {

    private static final LoggerProtocol LOGGER = LogManager.getLogger("agent_builder");

    public static final String DL_GENERATE_SYSTEM_PROMPT_TEMPLATE = """
            ## 人设
            你是一名工作流大师，你可以基于给定的任务描述思考并创建由节点连接组成的具体流程图。

            ## 任务描述
            - 你的任务是根据给定的工作流设计文档，使用所提供的节点信息及其 schema，生成一个字符串 json 来表征工作流。

            ## 节点信息
            {{components}}

            ## 节点schema
            schema中会出现的所有字段说明：
            ```json
            {
              "id": "节点在工作流中的唯一标识符，用于被其他节点引用",
              "type": "节点类型",
              "description": "对节点用途的文字说明",
              "next": "节点执行完毕后默认跳转的下一个节点ID（仅部分节点使用）",
              "parameters": {
                "inputs": [{"name": "输入参数的名称", "value": "输入参数的值或来源"}],
                "outputs": [{"name": "输出参数的名称", "description": "对该输出参数的含义或用途的说明"}],
                "configs": {
                  "system_prompt": "LLM 节点使用的系统提示词",
                  "user_prompt": "用户提示词模板",
                  "template": "用于 Output、End 等节点的模板",
                  "prompt": "用于 IntentDetection、Questioner 等节点的文本提示配置",
                  "code": "Code 节点中实际执行的 Python 代码字符串",
                  "tool_id": "插件节点使用的工具唯一标识",
                  "tool_name": "插件的名称"
                },
                "conditions": [{
                  "branch": "分支标识符",
                  "description": "对该分支适用场景的说明",
                  "expression": "用于判断是否进入该条件分支的逻辑表达式",
                  "next": "当前条件命中后将跳转到的下一个节点 ID"
                }]
              }
            }
            ```

            各节点schema使用说明：
            {{schema}}

            ## 可以使用的插件信息
            {{plugins}}

            ## 规则限制
            1. 绝对遵守各节点的schema格式和限制
            2. parameters的inputs中的元素为引用赋值时，只能引用其他节点中parameters中outputs中的变量
            3. 输出字符串形式的json，模仿示例的字符串形式的json进行输出

            ## 示例（示例内容均遵循标准schema）
            {{examples}}
            """;

    public static final String DL_REFINE_USER_PROMPT_TEMPLATE = """
            需要你按照用户输入的要求，基于已有流程图和工作流内容，进行修改和完善，确保其符合要求并且没有错误。
            ## 用户输入
            {{user_input}}

            ## 已有流程图内容
            {{exist_mermaid}}

            ## 已有工作流内容
            {{exist_dl}}
            """;

    public static final PromptTemplate DL_GENERATE_SYSTEM_TEMPLATE = PromptTemplate.builder()
            .content(List.of(new UserMessage(DL_GENERATE_SYSTEM_PROMPT_TEMPLATE)))
            .build();

    public static final PromptTemplate DL_REFINE_USER_TEMPLATE = PromptTemplate.builder()
            .content(List.of(new UserMessage(DL_REFINE_USER_PROMPT_TEMPLATE)))
            .build();

    private final Model llm;
    private final List<BaseMessage> reflectPrompts = new ArrayList<>();
    private final String componentsInfo;
    private final String schemaInfo;
    private final String examples;

    public DLGenerator(Model llm) {
        this.llm = llm;
        SchemaExamples schemaExamples = loadSchemaAndExamples();
        this.componentsInfo = schemaExamples.componentsInfo();
        this.schemaInfo = schemaExamples.schemaInfo();
        this.examples = schemaExamples.examples();
    }

    public static SchemaExamples loadSchemaAndExamples() {
        return new SchemaExamples(DlAssets.COMPONENTS_INFO, DlAssets.SCHEMA_INFO, DlAssets.EXAMPLES);
    }

    public String generate(String query, Map<String, ? extends List<? extends Map<String, ?>>> resource) {
        String systemPrompt = updatePrompt(resource);
        return execute(query, systemPrompt);
    }

    public String refine(String query, Map<String, ? extends List<? extends Map<String, ?>>> resource,
                         String existDl, String existMermaid) {
        String systemPrompt = updatePrompt(resource);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("user_input", query);
        values.put("exist_dl", existDl);
        values.put("exist_mermaid", existMermaid);
        String userContent = DL_REFINE_USER_TEMPLATE.format(values).toMessages().get(0).getContentAsString();
        return execute(userContent, systemPrompt);
    }

    public List<BaseMessage> getReflectPrompts() {
        return reflectPrompts;
    }

    String updatePrompt(Map<String, ? extends List<? extends Map<String, ?>>> resource) {
        List<? extends Map<String, ?>> plugins = resource == null ? null : resource.get("plugins");
        String pluginsString = plugins == null || plugins.isEmpty()
                ? WorkflowPrompts.EMPTY_RESOURCE_CONTENT
                : plugins.stream().map(DLGenerator::pythonRepr).collect(Collectors.joining("\n"));

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("components", componentsInfo);
        values.put("schema", schemaInfo);
        values.put("examples", examples);
        values.put("plugins", pluginsString);
        return DL_GENERATE_SYSTEM_TEMPLATE.format(values).toMessages().get(0).getContentAsString();
    }

    private String execute(String query, String systemPrompt) {
        List<BaseMessage> prompts = new ArrayList<>();
        prompts.add(new SystemMessage(systemPrompt));
        prompts.add(new UserMessage(query));
        prompts.addAll(reflectPrompts);

        AssistantMessage response = llm.invoke(prompts).toCompletableFuture().join();
        String generatedDl = response.getContentAsString();
        LOGGER.debug("DL generation completed, output_length={}", generatedDl.length());
        return generatedDl;
    }

    private static String pythonRepr(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof String text) {
            return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'";
        }
        if (value instanceof Boolean bool) {
            return bool ? "True" : "False";
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> pythonRepr(String.valueOf(entry.getKey())) + ": " + pythonRepr(entry.getValue()))
                    .collect(Collectors.joining(", ", "{", "}"));
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(DLGenerator::pythonRepr).collect(Collectors.joining(", ", "[", "]"));
        }
        return String.valueOf(value);
    }

    /**
     * Tuple-style schema/example payload.
     */
    public record SchemaExamples(String componentsInfo, String schemaInfo, String examples) {
    }
}
