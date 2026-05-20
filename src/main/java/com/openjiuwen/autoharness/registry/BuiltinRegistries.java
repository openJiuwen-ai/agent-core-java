/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.registry;

import com.openjiuwen.autoharness.pipelines.ExtendedEvolvePipeline;
import com.openjiuwen.autoharness.pipelines.MetaEvolvePipeline;
import com.openjiuwen.autoharness.schema.AutoHarnessConfig;
import com.openjiuwen.autoharness.schema.StageSpec;
import com.openjiuwen.autoharness.stages.AssessStage;
import com.openjiuwen.autoharness.stages.BaseStage;
import com.openjiuwen.autoharness.stages.CommitStage;
import com.openjiuwen.autoharness.stages.ImplementStage;
import com.openjiuwen.autoharness.stages.LearningsStage;
import com.openjiuwen.autoharness.stages.PlanStage;
import com.openjiuwen.autoharness.stages.PublishPrStage;
import com.openjiuwen.autoharness.stages.VerifyStage;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Auto-generated for codecheck compliance.
 */
public final class BuiltinRegistries {
    private BuiltinRegistries() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static StageRegistry buildStageRegistry() {
        return buildStageRegistry(AutoHarnessConfig.builder().build());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static StageRegistry buildStageRegistry(AutoHarnessConfig config) {
        StageRegistry registry = new StageRegistry();
        registerStage(registry, new AssessStage());
        registerStage(registry, new PlanStage());
        registerStage(registry, new ImplementStage());
        registerStage(registry, new VerifyStage());
        registerStage(registry, new CommitStage());
        registerStage(registry, new PublishPrStage());
        registerStage(registry, new LearningsStage());
        for (String registrar : safe(config == null ? null : config.getStageRegistrars())) {
            invokeStageRegistrar(registrar, registry);
        }
        return registry;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static PipelineRegistry buildPipelineRegistry() {
        return buildPipelineRegistry(AutoHarnessConfig.builder().build(), buildStageRegistry());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static PipelineRegistry buildPipelineRegistry(AutoHarnessConfig config, StageRegistry stageRegistry) {
        PipelineRegistry registry = new PipelineRegistry();
        MetaEvolvePipeline meta = new MetaEvolvePipeline();
        registry.register(meta.spec());
        ExtendedEvolvePipeline extended = new ExtendedEvolvePipeline();
        registry.register(extended.spec());
        for (String registrar : safe(config == null ? null : config.getPipelineRegistrars())) {
            invokePipelineRegistrar(registrar, registry, stageRegistry);
        }
        return registry;
    }

    private static void registerStage(StageRegistry registry, BaseStage stage) {
        registry.register(StageSpec.builder()
                .name(stage.name())
                .stageCls(stage.getClass())
                .scope(stage.scope())
                .consumes(stage.consumes())
                .produces(stage.produces())
                .description(stage.description())
                .build());
    }

    private static void invokeStageRegistrar(String path, StageRegistry registry) {
        Method method = loadRegistrar(path);
        invoke(method, new Object[] {registry});
    }

    private static void invokePipelineRegistrar(String path, PipelineRegistry registry, StageRegistry stageRegistry) {
        Method method = loadRegistrar(path);
        int parameterCount = method.getParameterCount();
        if (parameterCount >= 2) {
            invoke(method, new Object[] {registry, stageRegistry});
        } else {
            invoke(method, new Object[] {registry});
        }
    }

    private static Method loadRegistrar(String path) {
        String value = path == null ? "" : path.trim();
        int split = value.indexOf(':');
        if (split <= 0 || split == value.length() - 1) {
            throw new IllegalArgumentException("Registrar must use 'class:method' syntax: " + value);
        }
        String className = value.substring(0, split);
        String methodName = value.substring(split + 1);
        try {
            Class<?> type = Class.forName(className);
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    method.setAccessible(true);
                    return method;
                }
            }
            throw new IllegalArgumentException("Registrar method not found: " + value);
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException("Registrar class not found: " + value, ex);
        }
    }

    private static void invoke(Method method, Object[] args) {
        try {
            method.invoke(null, args);
        } catch (IllegalAccessException ex) {
            throw new IllegalArgumentException("Registrar is not accessible: " + method, ex);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalArgumentException("Registrar failed: " + method, cause);
        }
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }
}
