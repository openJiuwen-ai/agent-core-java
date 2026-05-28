/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

/**
 * DL generator — generates DL from natural language using LLM.
 * <p>
 * Mirrors Python's {@code DLGenerator} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.dl_generator}.
 */
public class DlGenerator {

    private final Object llm;

    public DlGenerator(Object llm) {
        this.llm = llm;
    }

    public Object getLlm() { return llm; }

    public static String formatGenerateSystemTemplate(String components, String schema, String plugins, String examples) {
        return "Components: " + components + "\nSchema: " + schema + "\nPlugins: " + plugins + "\nExamples: " + examples;
    }

    public static String formatRefineUserTemplate(String userInput, String existMermaid, String existDl) {
        return "Input: " + userInput + "\nMermaid: " + existMermaid + "\nDL: " + existDl;
    }
}
