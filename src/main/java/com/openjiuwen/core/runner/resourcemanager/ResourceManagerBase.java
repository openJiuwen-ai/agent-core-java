/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.multi_agent.BaseTeam;
import com.openjiuwen.core.multi_agent.schema.TeamCard;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Module-level provider aliases and tag constants.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.runner.resources_manager.base} in
 * {@code openjiuwen/core/runner/resources_manager/base.py}.</p>
 */
public final class ResourceManagerBase {

    /**
     * Mirrors Python's {@code ALL} in
     * {@code openjiuwen/core/runner/resources_manager/base.py}.
     */
    public static final String ALL = "*";

    /**
     * Mirrors Python's {@code GLOBAL} in
     * {@code openjiuwen/core/runner/resources_manager/base.py}.
     */
    public static final String GLOBAL = "__global__";

    /**
     * Mirrors Python's {@code ACTIVE} in
     * {@code openjiuwen/core/runner/resources_manager/base.py}.
     */
    public static final String ACTIVE = "__active__";

    /**
     * Mirrors Python's {@code INACTIVE} in
     * {@code openjiuwen/core/runner/resources_manager/base.py}.
     */
    public static final String INACTIVE = "__inactive__";

    private ResourceManagerBase() {
    }

    /**
     * Mirrors Python's {@code AgentProvider} alias in
     * {@code openjiuwen/core/runner/resources_manager/base.py}.
     *
     * @param <A> agent type
     */
    @FunctionalInterface
    public interface AgentProvider<A> {

        CompletionStage<A> provide(AgentCard card);

        static <A> AgentProvider<A> fromSync(Function<AgentCard, A> provider) {
            return card -> CompletableFuture.completedFuture(provider.apply(card));
        }

        static <A> AgentProvider<A> fromAsync(Function<AgentCard, CompletionStage<A>> provider) {
            return provider::apply;
        }
    }

    /**
     * Mirrors Python's {@code AgentTeamProvider} alias in
     * {@code openjiuwen/core/runner/resources_manager/base.py}.
     *
     * @param <T> team type
     */
    @FunctionalInterface
    public interface AgentTeamProvider<T extends BaseTeam> {

        CompletionStage<T> provide(TeamCard card);

        static <T extends BaseTeam> AgentTeamProvider<T> fromSync(Function<TeamCard, T> provider) {
            return card -> CompletableFuture.completedFuture(provider.apply(card));
        }

        static <T extends BaseTeam> AgentTeamProvider<T> fromAsync(Function<TeamCard, CompletionStage<T>> provider) {
            return provider::apply;
        }
    }

    /**
     * Mirrors Python's {@code WorkflowProvider} alias in
     * {@code openjiuwen/core/runner/resources_manager/base.py}.
     *
     * @param <W> workflow type
     */
    @FunctionalInterface
    public interface WorkflowProvider<W extends Workflow> {

        CompletionStage<W> provide(WorkflowCard card);

        static <W extends Workflow> WorkflowProvider<W> fromSync(Function<WorkflowCard, W> provider) {
            return card -> CompletableFuture.completedFuture(provider.apply(card));
        }

        static <W extends Workflow> WorkflowProvider<W> fromAsync(Function<WorkflowCard, CompletionStage<W>> provider) {
            return provider::apply;
        }
    }

    /**
     * Mirrors Python's {@code ModelProvider} alias in
     * {@code openjiuwen/core/runner/resources_manager/base.py}.
     *
     * <p>The varargs are intentionally dynamic because the Python alias is
     * {@code Callable[[...], BaseModel]} and accepts arbitrary initialization
     * arguments.</p>
     *
     * @param <M> model type
     */
    @FunctionalInterface
    public interface ModelProvider<M> {

        CompletionStage<M> provide(Object... args);

        static <M> ModelProvider<M> fromSync(Function<Object[], M> provider) {
            return args -> CompletableFuture.completedFuture(provider.apply(args));
        }

        static <M> ModelProvider<M> fromAsync(Function<Object[], CompletionStage<M>> provider) {
            return provider::apply;
        }
    }
}
