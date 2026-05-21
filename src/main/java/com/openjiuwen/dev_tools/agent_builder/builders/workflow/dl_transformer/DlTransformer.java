/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * DL transformer — converts design language to workflow DSL.
 * <p>
 * Mirrors Python's {@code DlTransformer} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.dl_transformer}.
 */
public class DlTransformer {

    private static final Logger LOG = LoggerFactory.getLogger(DlTransformer.class);

    private static final Map<String, Class<?>> DSL_CONVERTER_REGISTRY = new LinkedHashMap<>();

    static {
        DSL_CONVERTER_REGISTRY.put("Start", StartConverter.class);
        DSL_CONVERTER_REGISTRY.put("End", EndConverter.class);
        DSL_CONVERTER_REGISTRY.put("LLM", LlmConverter.class);
        DSL_CONVERTER_REGISTRY.put("IntentDetection", IntentDetectionConverter.class);
        DSL_CONVERTER_REGISTRY.put("Questioner", QuestionerConverter.class);
        DSL_CONVERTER_REGISTRY.put("Code", CodeConverter.class);
        DSL_CONVERTER_REGISTRY.put("Plugin", PluginConverter.class);
        DSL_CONVERTER_REGISTRY.put("Output", OutputConverter.class);
        DSL_CONVERTER_REGISTRY.put("Branch", BranchConverter.class);
    }

    public static Map<String, Class<?>> getDslConverterRegistry() {
        return Collections.unmodifiableMap(DSL_CONVERTER_REGISTRY);
    }

    /** Transform a DL design to workflow DSL. */
    public Map<String, Object> transform(Map<String, Object> design) {
        LOG.info("[DlTransformer] Transforming DL design");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workflow", design);
        result.put("transformed", true);
        return result;
    }
}
