/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's converter package export behavior in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/__init__.py}.
 */
class ConvertersPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertEquals(
                "openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/__init__.py",
                ConvertersPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "BaseConverter",
                "StartConverter",
                "EndConverter",
                "LLMConverter",
                "IntentDetectionConverter",
                "QuestionerConverter",
                "CodeConverter",
                "PluginConverter",
                "OutputConverter",
                "BranchConverter"
        ), ConvertersPackage.EXPORTED_SYMBOLS);
        assertEquals(BaseConverter.class, ConvertersPackage.EXPORTED_TYPES.get("BaseConverter"));
        assertEquals(StartConverter.class, ConvertersPackage.EXPORTED_TYPES.get("StartConverter"));
        assertEquals(EndConverter.class, ConvertersPackage.EXPORTED_TYPES.get("EndConverter"));
        assertEquals(LLMConverter.class, ConvertersPackage.EXPORTED_TYPES.get("LLMConverter"));
        assertEquals(IntentDetectionConverter.class, ConvertersPackage.EXPORTED_TYPES.get("IntentDetectionConverter"));
        assertEquals(QuestionerConverter.class, ConvertersPackage.EXPORTED_TYPES.get("QuestionerConverter"));
        assertEquals(CodeConverter.class, ConvertersPackage.EXPORTED_TYPES.get("CodeConverter"));
        assertEquals(PluginConverter.class, ConvertersPackage.EXPORTED_TYPES.get("PluginConverter"));
        assertEquals(OutputConverter.class, ConvertersPackage.EXPORTED_TYPES.get("OutputConverter"));
        assertEquals(BranchConverter.class, ConvertersPackage.EXPORTED_TYPES.get("BranchConverter"));
    }
}
