/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package bridge for DL transformer converter exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.converters}
 * in {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/__init__.py}.</p>
 */
public final class ConvertersPackage {

    public static final String PYTHON_MODULE =
            "openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/__init__.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
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
    );
    public static final Map<String, Class<? extends BaseConverter>> EXPORTED_TYPES = exportedTypes();

    private ConvertersPackage() {
    }

    private static Map<String, Class<? extends BaseConverter>> exportedTypes() {
        Map<String, Class<? extends BaseConverter>> exports = new LinkedHashMap<>();
        exports.put("BaseConverter", BaseConverter.class);
        exports.put("StartConverter", StartConverter.class);
        exports.put("EndConverter", EndConverter.class);
        exports.put("LLMConverter", LLMConverter.class);
        exports.put("IntentDetectionConverter", IntentDetectionConverter.class);
        exports.put("QuestionerConverter", QuestionerConverter.class);
        exports.put("CodeConverter", CodeConverter.class);
        exports.put("PluginConverter", PluginConverter.class);
        exports.put("OutputConverter", OutputConverter.class);
        exports.put("BranchConverter", BranchConverter.class);
        return Map.copyOf(exports);
    }
}
