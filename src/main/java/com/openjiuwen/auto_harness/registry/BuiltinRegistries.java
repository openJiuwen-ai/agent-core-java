package com.openjiuwen.auto_harness.registry;

import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.pipelines.ExtendedEvolvePipeline;
import com.openjiuwen.auto_harness.pipelines.MetaEvolvePipeline;
import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.PipelineSpec;
import com.openjiuwen.auto_harness.schema.StageSpec;
import com.openjiuwen.auto_harness.stages.AssessStage;
import com.openjiuwen.auto_harness.stages.CommitStage;
import com.openjiuwen.auto_harness.stages.ImplementStage;
import com.openjiuwen.auto_harness.stages.LearningsStage;
import com.openjiuwen.auto_harness.stages.PlanStage;
import com.openjiuwen.auto_harness.stages.PublishPrStage;
import com.openjiuwen.auto_harness.stages.VerifyStage;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Mirrors Python's builtin registry builders in {@code openjiuwen.auto_harness.registry.builtin}.
 */
public final class BuiltinRegistries {

    private BuiltinRegistries() {
    }

    public static StageRegistry registerBuiltinStages(StageRegistry registry) {
        for (Class<?> stageClass : List.of(
                AssessStage.class,
                PlanStage.class,
                ImplementStage.class,
                VerifyStage.class,
                CommitStage.class,
                PublishPrStage.class,
                LearningsStage.class
        )) {
            registry.register(specFromStage(stageClass));
        }
        return registry;
    }

    public static StageRegistry buildStageRegistry(AutoHarnessConfig config) {
        StageRegistry registry = registerBuiltinStages(new StageRegistry());
        for (String path : config.getStageRegistrars()) {
            invokeRegistrar(path, registry, null);
        }
        return registry;
    }

    public static PipelineRegistry buildPipelineRegistry(AutoHarnessConfig config, StageRegistry stageRegistry) {
        PipelineRegistry registry = new PipelineRegistry();
        registry.register(specFromPipeline(MetaEvolvePipeline.class));
        registry.register(specFromPipeline(ExtendedEvolvePipeline.class));
        for (String path : config.getPipelineRegistrars()) {
            invokeRegistrar(path, registry, stageRegistry);
        }
        return registry;
    }

    private static StageSpec specFromStage(Class<?> clazz) {
        try {
            Object instance = clazz.getDeclaredConstructor().newInstance();
            Method method = clazz.getMethod("spec");
            return (StageSpec) method.invoke(instance);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create stage spec for " + clazz.getName(), e);
        }
    }

    private static PipelineSpec specFromPipeline(Class<?> clazz) {
        try {
            Object instance = clazz.getDeclaredConstructor().newInstance();
            Method method = clazz.getMethod("spec");
            return (PipelineSpec) method.invoke(instance);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create pipeline spec for " + clazz.getName(), e);
        }
    }

    private static void invokeRegistrar(String path, Object primaryRegistry, StageRegistry stageRegistry) {
        try {
            int separator = path.indexOf(':');
            if (separator <= 0 || separator == path.length() - 1) {
                throw new IllegalArgumentException("Registrar must use 'module:callable' syntax: " + path);
            }
            String className = path.substring(0, separator);
            String methodName = path.substring(separator + 1);
            Class<?> clazz = Class.forName(className);
            Method twoArg = null;
            for (Method candidate : clazz.getMethods()) {
                if (!candidate.getName().equals(methodName)) {
                    continue;
                }
                if (candidate.getParameterCount() == 2) {
                    candidate.setAccessible(true);
                    twoArg = candidate;
                    break;
                }
                if (candidate.getParameterCount() == 1) {
                    candidate.setAccessible(true);
                    candidate.invoke(null, primaryRegistry);
                    return;
                }
            }
            if (twoArg != null) {
                twoArg.setAccessible(true);
                twoArg.invoke(null, primaryRegistry, stageRegistry);
                return;
            }
            throw new NoSuchMethodException(path);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to invoke registrar: " + path, e);
        }
    }
}
