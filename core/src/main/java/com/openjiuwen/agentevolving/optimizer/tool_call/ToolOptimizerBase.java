/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agentevolving.optimizer.BaseOptimizer;
import com.openjiuwen.agentevolving.optimizer.tool_call.utils.DefaultConfigs;
import com.openjiuwen.agentevolving.optimizer.tool_call.utils.SchemaExtractor;
import com.openjiuwen.core.operator.Operator;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool dimension optimizer base class.
 *
 * <p>Mirrors Python's {@code ToolOptimizerBase} in
 * {@code openjiuwen/agent_evolving/optimizer/tool_call/base.py}.</p>
 */
public abstract class ToolOptimizerBase extends BaseOptimizer {

    public static final String TOOL_DESCRIPTION_TARGET = "tool_description";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected int maxTurns;
    protected String llmApiKey;
    protected Map<String, Object> configEg;
    protected Map<String, Object> configDesc;
    protected String pathSaveDir;

    protected ToolOptimizerBase() {
        this(Map.of());
    }

    protected ToolOptimizerBase(Map<String, Object> kwargs) {
        this.domain = "tool";
        Map<String, Object> options = kwargs == null ? Map.of() : kwargs;
        this.maxTurns = intOption(options, "max_turns", 5);
        this.llmApiKey = stringOption(options, "llm_api_key", "");
        this.configEg = mapOption(options, "config_eg", DefaultConfigs.defaultConfigEg());
        this.configDesc = mapOption(options, "config_desc", DefaultConfigs.defaultConfigDesc());
        this.pathSaveDir = stringOption(options, "path_save_dir", "./tool_optimizer_results");
        String toolName = stringOption(options, "tool_name", "tool");

        this.configEg.put("save_dir", Paths.get(pathSaveDir, "examples").toString());
        this.configDesc.put("save_dir", Paths.get(pathSaveDir, "descriptions").toString());
        this.configDesc.put("examples_dir", this.configEg.get("save_dir"));
        this.configDesc.put("neg_ex_input_path", Paths.get(pathSaveDir, toolName + ".json").toString());
    }

    @Override
    public List<String> defaultTargets() {
        return List.of(TOOL_DESCRIPTION_TARGET);
    }

    /**
     * Optimize tool description and examples using the current Python pipeline shape.
     *
     * @param tool tool payload containing at least {@code name} and {@code description}
     * @param toolCallable callable boundary passed to the example stage
     * @return final formatted description
     */
    public Map<String, Object> optimizeTool(Map<String, Object> tool, Object toolCallable) {
        Map<String, Object> workingTool = tool == null ? new LinkedHashMap<>() : new LinkedHashMap<>(tool);
        Object originalDesc = workingTool.get("description");
        List<Object> resultDescs = new ArrayList<>();

        for (int i = 0; i < maxTurns; i++) {
            if (i > 0) {
                String latestDescription = extractDescription(resultDescs.get(resultDescs.size() - 1), false);
                workingTool.put("description", latestDescription);
            }

            configDesc.put("llm_api_key", llmApiKey);
            configEg.put("llm_api_key", llmApiKey);
            runCustomizedPipeline("example", workingTool, toolCallable, configEg);
            resultDescs.add(runCustomizedPipeline("description", workingTool, toolCallable, configDesc));
        }

        String outputDesc = resultDescs.isEmpty()
                ? stringValue(workingTool.get("description"))
                : extractDescription(resultDescs.get(resultDescs.size() - 1), true);
        Object reviewer = createToolDescriptionReviewer(stringValue(configDesc.get("eval_model_id")), llmApiKey);
        Map<String, Object> schema = SchemaExtractor.extractSchema(originalDesc);
        Object processed = processDescription(
                reviewer,
                outputDesc,
                stringValue(workingTool.get("description")),
                List.of("clean", "cross_check", "translate")
        );
        return formatDescription(reviewer, schema, processed, null);
    }

    public int getMaxTurns() {
        return maxTurns;
    }

    public String getLlmApiKey() {
        return llmApiKey;
    }

    public Map<String, Object> getConfigEg() {
        return new LinkedHashMap<>(configEg);
    }

    public Map<String, Object> getConfigDesc() {
        return new LinkedHashMap<>(configDesc);
    }

    public String getPathSaveDir() {
        return pathSaveDir;
    }

    public Map<String, Operator> getOperators() {
        return new LinkedHashMap<>(operators);
    }

    @SuppressWarnings("unchecked")
    protected List<Object> runCustomizedPipeline(
            String stage,
            Map<String, Object> tool,
            Object toolCallable,
            Map<String, Object> config
    ) {
        try {
            Class<?> pipelineClass = Class.forName(
                    "com.openjiuwen.agentevolving.optimizer.tool_call.utils.CustomizedPipeline"
            );
            Method method = pipelineClass.getMethod(
                    "customizedPipeline",
                    String.class,
                    Map.class,
                    Map.class,
                    Object.class
            );
            Object result = method.invoke(null, stage, tool, config, toolCallable);
            if (result instanceof List<?> list) {
                return new ArrayList<>((List<Object>) list);
            }
            return List.of(result);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("customized_pipeline dependency is unavailable", exception);
        } catch (InvocationTargetException exception) {
            throw propagate(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("customized_pipeline dependency could not be invoked", exception);
        }
    }

    protected Object createToolDescriptionReviewer(String evalModelId, String apiKey) {
        try {
            Class<?> reviewerClass = Class.forName(
                    "com.openjiuwen.agentevolving.optimizer.tool_call.utils.ToolDescriptionReviewer"
            );
            Constructor<?> constructor = reviewerClass.getConstructor(String.class, String.class);
            return constructor.newInstance(evalModelId, apiKey);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("ToolDescriptionReviewer dependency is unavailable", exception);
        } catch (InvocationTargetException exception) {
            throw propagate(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("ToolDescriptionReviewer dependency could not be created", exception);
        }
    }

    protected Object processDescription(Object reviewer, Object data, String originalTool, List<String> steps) {
        try {
            Method method = reviewer.getClass().getMethod("process", Object.class, String.class, List.class);
            return method.invoke(reviewer, data, originalTool, steps);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("ToolDescriptionReviewer.process(Object, String, List) is unavailable",
                    exception);
        } catch (InvocationTargetException exception) {
            throw propagate(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("ToolDescriptionReviewer.process could not be invoked", exception);
        }
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> formatDescription(
            Object reviewer,
            Map<String, Object> schema,
            Object description,
            String example
    ) {
        try {
            Method method;
            Object payload = description;
            try {
                method = reviewer.getClass().getMethod("format", Map.class, Object.class, String.class);
            } catch (NoSuchMethodException exception) {
                method = reviewer.getClass().getMethod("format", Map.class, String.class, String.class);
                payload = descriptionToText(description);
            }
            Object result = method.invoke(reviewer, schema, payload, example);
            if (result instanceof Map<?, ?> map) {
                Map<String, Object> copied = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    copied.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                return copied;
            }
            return Map.of("description", result);
        } catch (InvocationTargetException exception) {
            throw propagate(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("ToolDescriptionReviewer.format could not be invoked", exception);
        }
    }

    private static String extractDescription(Object value, boolean useLastLeaf) {
        Object outer = lastElement(value);
        Object leaf = useLastLeaf ? lastElement(outer) : firstElement(outer);
        if (leaf instanceof Map<?, ?> map && map.get("description") != null) {
            return String.valueOf(map.get("description"));
        }
        return stringValue(leaf);
    }

    private static Object firstElement(Object value) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            return list.get(0);
        }
        return value;
    }

    private static Object lastElement(Object value) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            return list.get(list.size() - 1);
        }
        return value;
    }

    private static int intOption(Map<String, Object> options, String key, int defaultValue) {
        Object value = options.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
    }

    private static String stringOption(Map<String, Object> options, String key, String defaultValue) {
        Object value = options.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static Map<String, Object> mapOption(
            Map<String, Object> options,
            String key,
            Map<String, Object> defaultValue
    ) {
        Object raw = options.get(key);
        Map<String, Object> result = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        result.putAll(defaultValue);
        return result;
    }

    private static String descriptionToText(Object description) {
        if (description == null || description instanceof String) {
            return stringValue(description);
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(description);
        } catch (Exception exception) {
            return String.valueOf(description);
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static RuntimeException propagate(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RuntimeException(throwable);
    }
}
