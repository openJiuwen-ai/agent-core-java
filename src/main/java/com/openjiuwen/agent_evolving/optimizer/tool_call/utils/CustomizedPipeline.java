/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.openjiuwen.core.common.logging.Loggers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Tool optimization pipeline entrypoint.
 *
 * <p>Mirrors Python's {@code customized_pipeline} in
 * {@code openjiuwen/agent_evolving/optimizer/tool_call/utils/customized_pipline.py}.</p>
 */
public class CustomizedPipeline {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ObjectWriter PRETTY_WRITER = OBJECT_MAPPER.writerWithDefaultPrettyPrinter();
    private static final TypeReference<List<Object>> LIST_OF_OBJECTS = new TypeReference<>() {
    };

    /**
     * Run the Python module-level customized pipeline function.
     *
     * @param stage pipeline stage, either {@code example} or {@code description}
     * @param tool tool definition dictionary
     * @param config pipeline configuration dictionary
     * @param toolCallable optional callable used by {@link SimpleApiWrapperFromCallable}
     * @return newly generated results, merged with any previous saved results
     */
    public static List<Object> customizedPipeline(
            String stage,
            Map<String, Object> tool,
            Map<String, Object> config,
            Object toolCallable
    ) {
        return new CustomizedPipeline().run(stage, tool, config, toolCallable);
    }

    /**
     * Instance implementation used by tests to override runtime-only dependencies.
     *
     * @param stage pipeline stage, either {@code example} or {@code description}
     * @param tool tool definition dictionary
     * @param config pipeline configuration dictionary
     * @param toolCallable optional callable used by {@link SimpleApiWrapperFromCallable}
     * @return newly generated results, merged with any previous saved results
     */
    public List<Object> run(
            String stage,
            Map<String, Object> tool,
            Map<String, Object> config,
            Object toolCallable
    ) {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(config, "config");

        if (config.containsKey("fn_call_path")) {
            throw new IllegalStateException("config based api wrapper is " + "not " + "implemented yet.");
        }
        if (toolCallable == null) {
            throw new IllegalArgumentException("Either config or tool_callable must be provided.");
        }

        String toolName = stringValue(requireValue(tool, "name", "tool"));
        SimpleApiWrapperFromCallable callApiFn = createApiWrapper(toolCallable, toolName, config);
        SimpleEval evalFn = createEval(callApiFn, config);
        List<String> apiKeys = null;
        List<String> nonOptParams = new ArrayList<>();

        Object method;
        if ("example".equals(stage)) {
            method = createExampleMethod(config, callApiFn, evalFn, apiKeys, nonOptParams);
        } else if ("description".equals(stage)) {
            method = createDescriptionMethod(config, evalFn);
        } else {
            throw new IllegalArgumentException("wrong stage: " + stage);
        }

        Loggers.AGENT.info("=== Starting SingleRoundSearch ===");
        BeamSearch singleSearch = createBeamSearch(
                method,
                requiredInt(config, "beam_width"),
                requiredInt(config, "expand_num"),
                requiredInt(config, "max_depth"),
                requiredInt(config, "num_workers"),
                requiredBoolean(config, "verbose"),
                true,
                true,
                3.0d,
                requiredInt(config, "top_k")
        );

        List<Object> result = new ArrayList<>(search(singleSearch, tool));
        return saveAndMerge(result, toolName, requiredString(config, "save_dir"));
    }

    protected SimpleApiWrapperFromCallable createApiWrapper(
            Object toolCallable,
            String toolName,
            Map<String, Object> config
    ) {
        return new SimpleApiWrapperFromCallable(toolCallable, toolName, config);
    }

    protected SimpleEval createEval(Object apiWrapper, Map<String, Object> config) {
        return new SimpleEval(apiWrapper, config);
    }

    protected Object createExampleMethod(
            Map<String, Object> config,
            Object callApiFn,
            SimpleEval evalFn,
            List<String> apiKeys,
            List<String> nonOptParams
    ) {
        return constructByArity(
                "com.openjiuwen.agent_evolving.optimizer.tool_call.utils.APICallToExampleMethod",
                config,
                callApiFn,
                evalFn,
                apiKeys,
                nonOptParams
        );
    }

    protected Object createDescriptionMethod(Map<String, Object> config, SimpleEval evalFn) {
        return constructByArity(
                "com.openjiuwen.agent_evolving.optimizer.tool_call.utils.ToolDescriptionMethod",
                config,
                evalFn
        );
    }

    protected BeamSearch createBeamSearch(
            Object method,
            int beamWidth,
            int expandNum,
            int maxDepth,
            int numWorkers,
            boolean verbose,
            boolean earlyStop,
            boolean checkValid,
            double maxScore,
            int topK
    ) {
        return new BeamSearch(
                method,
                beamWidth,
                expandNum,
                maxDepth,
                numWorkers,
                verbose,
                earlyStop,
                checkValid,
                maxScore,
                topK
        );
    }

    protected List<?> search(BeamSearch singleSearch, Map<String, Object> tool) {
        return singleSearch.search(tool);
    }

    private static List<Object> saveAndMerge(List<Object> result, String toolName, String saveDir) {
        Path saveDirectory = Path.of(saveDir);
        Path savePath = saveDirectory.resolve(toolName + ".json");
        try {
            Files.createDirectories(saveDirectory);
            List<Object> mergedResult = new ArrayList<>();
            if (Files.exists(savePath)) {
                mergedResult.addAll(OBJECT_MAPPER.readValue(Files.readString(savePath), LIST_OF_OBJECTS));
            }
            mergedResult.addAll(result);
            Files.writeString(savePath, PRETTY_WRITER.writeValueAsString(mergedResult));
            return mergedResult;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save customized pipeline results.", exception);
        }
    }

    private static Object constructByArity(String className, Object... args) {
        try {
            Class<?> targetClass = Class.forName(className);
            for (Constructor<?> constructor : targetClass.getConstructors()) {
                if (constructor.getParameterCount() == args.length) {
                    return constructor.newInstance(args);
                }
            }
            throw new IllegalStateException("No compatible constructor found for " + className);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Pipeline dependency is unavailable: " + className, exception);
        } catch (InstantiationException | IllegalAccessException exception) {
            throw new IllegalStateException("Pipeline dependency could not be constructed: " + className, exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        }
    }

    private static int requiredInt(Map<String, Object> config, String key) {
        Object value = requireValue(config, key, "config");
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static boolean requiredBoolean(Map<String, Object> config, String key) {
        Object value = requireValue(config, key, "config");
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static String requiredString(Map<String, Object> config, String key) {
        return stringValue(requireValue(config, key, "config"));
    }

    private static Object requireValue(Map<String, Object> values, String key, String owner) {
        if (!values.containsKey(key)) {
            throw new IllegalArgumentException("Missing required " + owner + " key: " + key);
        }
        return values.get(key);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
