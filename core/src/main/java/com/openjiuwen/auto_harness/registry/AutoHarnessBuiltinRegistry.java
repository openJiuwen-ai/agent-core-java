/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.registry;

import com.openjiuwen.auto_harness.pipelines.extended_evolve_pipeline.ExtendedEvolvePipeline;
import com.openjiuwen.auto_harness.pipelines.meta_evolve_pipeline.MetaEvolvePipeline;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.PipelineSpec;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageSpec;
import com.openjiuwen.auto_harness.stages.BaseStage;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Built-in pipeline and stage registration helpers.
 *
 * <p>Mirrors Python's module helpers in
 * {@code openjiuwen/auto_harness/registry/builtin.py}.</p>
 */
public final class AutoHarnessBuiltinRegistry {

    private static final List<StageDescriptor> BUILTIN_STAGES = List.of(
            new StageDescriptor(
                    "assess",
                    "assess",
                    "Assess current state",
                    "Assess current repository state.",
                    "session",
                    List.of(),
                    List.of("assessment"),
                    "com.openjiuwen.auto_harness.stages.MetaAssessStage"
            ),
            new StageDescriptor(
                    "assess_ext",
                    "assess",
                    "Assess extension gaps",
                    "Analyze runtime extension capability gaps.",
                    "session",
                    List.of(),
                    List.of("gap_analysis"),
                    "com.openjiuwen.auto_harness.stages.ExtendAssessStage"
            ),
            new StageDescriptor(
                    "plan",
                    "plan",
                    "Plan optimization tasks",
                    "Plan optimization tasks.",
                    "session",
                    List.of("assessment"),
                    List.of("task_plan"),
                    "com.openjiuwen.auto_harness.stages.MetaPlanStage"
            ),
            new StageDescriptor(
                    "plan_ext",
                    "plan",
                    "Design runtime extensions",
                    "Design runtime extensions from analyzed gaps.",
                    "session",
                    List.of("gap_analysis"),
                    List.of("extension_design"),
                    "com.openjiuwen.auto_harness.stages.ExtendPlanStage"
            ),
            new StageDescriptor(
                    "implement",
                    "implement",
                    "Implement code changes",
                    "Implement code changes.",
                    "task",
                    List.of(),
                    List.of("code_change"),
                    "com.openjiuwen.auto_harness.stages.MetaImplementStage"
            ),
            new StageDescriptor(
                    "implement_ext",
                    "implement",
                    "Implement runtime extension",
                    "Materialize one extension design into the task worktree.",
                    "task",
                    List.of("extension_target"),
                    List.of("extension_build"),
                    "com.openjiuwen.auto_harness.stages.ExtendImplementStage"
            ),
            new StageDescriptor(
                    "verify",
                    "verify",
                    "Verify code changes",
                    "Verify code changes.",
                    "task",
                    List.of("code_change"),
                    List.of("verify_report"),
                    "com.openjiuwen.auto_harness.stages.MetaVerifyStage"
            ),
            new StageDescriptor(
                    "verify_ext",
                    "verify",
                    "Verify runtime extension",
                    "Validate manifest, imports, lint, and constructors.",
                    "task",
                    List.of("extension_build"),
                    List.of("extension_build", "verify_report"),
                    "com.openjiuwen.auto_harness.stages.ExtendVerifyStage"
            ),
            new StageDescriptor(
                    "commit",
                    "commit",
                    "Create commit",
                    "Create a git commit for the task.",
                    "task",
                    List.of("verify_report"),
                    List.of("commit_result"),
                    "com.openjiuwen.auto_harness.stages.CommitStage"
            ),
            new StageDescriptor(
                    "publish_pr",
                    "publish",
                    "Publish PR",
                    "Push branch and create PR when configured.",
                    "task",
                    List.of("verify_report", "commit_result"),
                    List.of("pull_request", "task_result"),
                    "com.openjiuwen.auto_harness.stages.PublishPRStage"
            ),
            new StageDescriptor(
                    "learnings",
                    "learnings",
                    "Record learnings",
                    "Record learnings after a session.",
                    "session",
                    List.of("session_results"),
                    List.of("session_results"),
                    "com.openjiuwen.auto_harness.stages.LearningsStage"
            )
    );

    private AutoHarnessBuiltinRegistry() {
    }

    public static StageRegistry registerBuiltinStages(StageRegistry registry) {
        StageRegistry target = registry == null ? new StageRegistry() : registry;
        for (StageDescriptor descriptor : BUILTIN_STAGES) {
            target.register(descriptor.toSpec());
        }
        return target;
    }

    public static StageRegistry buildStageRegistry(AutoHarnessConfig config) {
        AutoHarnessConfig resolvedConfig = config == null ? new AutoHarnessConfig() : config;
        StageRegistry registry = registerBuiltinStages(new StageRegistry());
        for (String path : resolvedConfig.getStageRegistrars()) {
            Registrar registrar = loadRegistrar(path);
            registrar.invoke(registry);
        }
        return registry;
    }

    public static PipelineRegistry buildPipelineRegistry(
            AutoHarnessConfig config,
            StageRegistry stageRegistry
    ) {
        AutoHarnessConfig resolvedConfig = config == null ? new AutoHarnessConfig() : config;
        StageRegistry resolvedStageRegistry = stageRegistry == null ? buildStageRegistry(resolvedConfig) : stageRegistry;
        PipelineRegistry registry = new PipelineRegistry();
        registry.register(new MetaEvolvePipeline().spec());
        registry.register(new ExtendedEvolvePipeline().spec());
        for (String path : resolvedConfig.getPipelineRegistrars()) {
            Registrar registrar = loadRegistrar(path);
            callPipelineRegistrar(registrar, registry, resolvedStageRegistry);
        }
        return registry;
    }

    public static PipelineRegistry buildPipelineRegistry(AutoHarnessConfig config) {
        AutoHarnessConfig resolvedConfig = config == null ? new AutoHarnessConfig() : config;
        return buildPipelineRegistry(resolvedConfig, buildStageRegistry(resolvedConfig));
    }

    static Registrar loadRegistrar(String path) {
        String raw = path == null ? "" : path;
        int sep = raw.indexOf(':');
        if (sep <= 0 || sep >= raw.length() - 1) {
            throw new IllegalArgumentException("Registrar must use 'module:callable' syntax: " + raw);
        }
        String className = raw.substring(0, sep);
        String methodName = raw.substring(sep + 1);
        try {
            Class<?> registrarClass = Class.forName(className);
            for (Method method : registrarClass.getMethods()) {
                if (method.getName().equals(methodName)) {
                    return new Registrar(registrarClass, method);
                }
            }
            throw new IllegalArgumentException("Registrar '" + raw + "' is not callable");
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Registrar module not found: " + className, e);
        }
    }

    static void callPipelineRegistrar(
            Registrar registrar,
            PipelineRegistry pipelineRegistry,
            StageRegistry stageRegistry
    ) {
        if (registrar.positionalCount() >= 2) {
            registrar.invoke(pipelineRegistry, stageRegistry);
            return;
        }
        registrar.invoke(pipelineRegistry);
    }

    private static Class<?> stageClass(String className) {
        try {
            return Class.forName(className).asSubclass(BaseStage.class);
        } catch (ClassNotFoundException e) {
            return DeferredBuiltinStage.class;
        }
    }

    /**
     * Registrar wrapper for Java's reflected equivalent of Python callables.
     *
     * <p>Mirrors Python's registrar callable loading in
     * {@code openjiuwen/auto_harness/registry/builtin.py}.</p>
     */
    static final class Registrar {
        private final Class<?> ownerClass;
        private final Method method;

        private Registrar(Class<?> ownerClass, Method method) {
            this.ownerClass = ownerClass;
            this.method = method;
        }

        int positionalCount() {
            return method.getParameterCount();
        }

        void invoke(Object... args) {
            try {
                Object target = Modifier.isStatic(method.getModifiers())
                        ? null
                        : ownerClass.getDeclaredConstructor().newInstance();
                method.invoke(target, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("Registrar invocation failed", cause);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Registrar invocation failed", e);
            }
        }
    }

    private record StageDescriptor(
            String name,
            String slot,
            String displayName,
            String description,
            String scope,
            List<String> consumes,
            List<String> produces,
            String className
    ) {
        private StageSpec toSpec() {
            return StageSpec.builder()
                    .name(name)
                    .slot(slot)
                    .description(description)
                    .scope(scope)
                    .consumes(List.copyOf(consumes))
                    .produces(List.copyOf(produces))
                    .stageCls(stageClass(className))
                    .build();
        }
    }

    /**
     * Deferred stage class used only when same-batch concrete stage classes are
     * not translated yet.
     *
     * <p>Mirrors Python's stage imports in
     * {@code openjiuwen/auto_harness/registry/builtin.py}.</p>
     */
    public static class DeferredBuiltinStage extends BaseStage {
    }
}
