/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

/**
 * DL assets constants.
 * <p>
 * Mirrors Python's {@code dl_assets} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.dl_assets}.
 */
public final class DlAssets {

    private DlAssets() {
    }

    public static final String COMPONENTS_INFO = """
Start: 开始节点 - workflow entry point
End: 结束节点 - workflow exit point
LLM: 大模型节点 - large language model node
IntentDetection: 意图识别节点 - intent detection node
Questioner: 提问节点 - questioner node
Code: 代码节点 - code execution node
Plugin: 插件节点 - plugin tool node
Output: 输出节点 - output node
Branch: 分支节点 - conditional branch node
""";

    public static final String SCHEMA_INFO = """
Node schema:
- id: unique identifier
- type: node type (Start, End, LLM, IntentDetection, Questioner, Code, Plugin, Output, Branch)
- description: node description
- parameters: node configuration including inputs, outputs, configs
- next: next node id or condition branches
""";

    public static final String EXAMPLES = """
Example workflow:
1. Start -> LLM -> End
2. Start -> IntentDetection -> Branch -> LLM -> End
3. Start -> Questioner -> Plugin -> End
""";
}
