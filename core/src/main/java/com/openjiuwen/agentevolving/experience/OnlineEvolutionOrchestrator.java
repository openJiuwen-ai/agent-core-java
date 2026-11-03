/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.experience;

import com.openjiuwen.agentevolving.ApplyResult;
import com.openjiuwen.agentevolving.Protocols;
import com.openjiuwen.agentevolving.UpdateExecution;
import com.openjiuwen.agentevolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agentevolving.checkpointing.EvolutionStore;
import com.openjiuwen.agentevolving.signal.EvolutionSignal;
import com.openjiuwen.agentevolving.signal.EvolutionSignals;
import com.openjiuwen.agentevolving.signal.EvolutionTarget;
import com.openjiuwen.agentevolving.trajectory.Trajectory;
import com.openjiuwen.agentevolving.trajectory.UpdateKey;
import com.openjiuwen.agentevolving.updater.Updater;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.skill_call.SkillExperienceOperator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Coordinate the shared online evolution pipeline for one skill target.
 *
 * <p>Mirrors Python's {@code OnlineEvolutionOrchestrator} in
 * {@code openjiuwen/agent_evolving/experience/online_orchestrator.py}.</p>
 */
public class OnlineEvolutionOrchestrator {

    private static final LoggerProtocol LOGGER = Loggers.COMMON;

    private final EvolutionStore store;
    private final Updater updater;
    private final ExperienceManagerPort manager;
    private final Map<String, SkillExperienceOperator> skillOps;
    private final String requestIdPrefix;
    private final String stageSource;

    public OnlineEvolutionOrchestrator(
            EvolutionStore store,
            Updater updater,
            ExperienceManagerPort manager,
            Map<String, SkillExperienceOperator> skillOps
    ) {
        this(store, updater, manager, skillOps, null, "experience_updater");
    }

    public OnlineEvolutionOrchestrator(
            EvolutionStore store,
            Updater updater,
            ExperienceManagerPort manager,
            Map<String, SkillExperienceOperator> skillOps,
            String requestIdPrefix,
            String stageSource
    ) {
        this.store = store;
        this.updater = updater;
        this.manager = manager;
        this.skillOps = skillOps == null ? new LinkedHashMap<>() : skillOps;
        this.requestIdPrefix = requestIdPrefix;
        this.stageSource = stageSource == null ? "experience_updater" : stageSource;
    }

    public CompletionStage<OnlineEvolutionResult> evolve(
            String skillName,
            List<EvolutionSignal> signals,
            boolean requiresApproval
    ) {
        return evolve(skillName, signals, null, "", null, requiresApproval, null, null);
    }

    public CompletionStage<OnlineEvolutionResult> evolve(
            String skillName,
            List<EvolutionSignal> signals,
            List<Map<String, Object>> messages,
            String userQuery,
            Trajectory trajectory,
            boolean requiresApproval,
            Map<String, Object> metadata,
            String source
    ) {
        List<EvolutionSignal> safeSignals = copyList(signals);
        if (skillName == null || skillName.isEmpty() || safeSignals.isEmpty()) {
            return CompletableFuture.completedFuture(result(
                    skillName,
                    "skipped_no_input",
                    null,
                    "online evolution skipped because skill_name or signals are empty"
            ));
        }
        if (!store.skillExists(skillName)) {
            return CompletableFuture.completedFuture(result(
                    skillName,
                    "skipped_skill_not_found",
                    null,
                    "online evolution skipped because skill '" + skillName + "' does not exist"
            ));
        }
        if (!store.skillDefinitionExists(skillName)) {
            return CompletableFuture.completedFuture(result(
                    skillName,
                    "skipped_skill_definition_not_found",
                    null,
                    "online evolution skipped because skill '" + skillName + "' is missing SKILL.md"
            ));
        }

        return buildContext(skillName, safeSignals, messages, userQuery, trajectory, metadata)
                .thenCompose(context -> generatePreviewOrFailure(skillName, context)
                        .thenCompose(previewOrFailure -> {
                            if (previewOrFailure instanceof OnlineEvolutionResult failureResult) {
                                return CompletableFuture.completedFuture(failureResult);
                            }
                            return finishEvolution(
                                    skillName,
                                    (LocalApplyPreview) previewOrFailure,
                                    (EvolutionContext) context,
                                    requiresApproval,
                                    source
                            );
                        }));
    }

    protected CompletionStage<LocalApplyPreview> generateLocalApplyPreview(EvolutionContext onlineContext) {
        SkillExperienceOperator operator = skillOps.get(onlineContext.getSkillName());
        if (operator == null) {
            operator = new SkillExperienceOperator(onlineContext.getSkillName());
            skillOps.put(onlineContext.getSkillName(), operator);
        }

        Map<String, Operator> operators = Map.of(operator.getOperatorId(), operator);
        updater.bind(
                operators,
                List.of(Protocols.EXPERIENCES_TARGET),
                Map.of("online_contexts", Map.of(onlineContext.getSkillName(), onlineContext))
        );

        List<Trajectory> trajectories = new ArrayList<>();
        if (onlineContext.getTrajectory() instanceof Trajectory trajectory) {
            trajectories.add(trajectory);
        }
        return updater.process(trajectories, onlineContext.getSignals(), Map.of())
                .thenApply(rawUpdates -> {
                    List<ApplyResult> applyResults = UpdateExecution.executeUpdates(
                            operators,
                            toUpdateMap(rawUpdates)
                    );
                    return manager.buildLocalApplyPreview(onlineContext.getSkillName(), applyResults);
                });
    }

    private CompletionStage<EvolutionContext> buildContext(
            String skillName,
            List<EvolutionSignal> signals,
            List<Map<String, Object>> messages,
            String userQuery,
            Trajectory trajectory,
            Map<String, Object> metadata
    ) {
        return store.readSkillContent(skillName, true)
                .thenCompose(skillContent -> store.getPendingRecords(skillName, EvolutionTarget.DESCRIPTION)
                        .thenCompose(descRecords -> store.getPendingRecords(skillName, EvolutionTarget.BODY)
                                .thenCompose(bodyRecords -> store.getPendingRecords(skillName, EvolutionTarget.SCRIPT)
                                        .thenApply(scriptRecords -> new EvolutionContext(
                                                skillName,
                                                signals,
                                                skillContent,
                                                copyMessageList(messages),
                                                descRecords,
                                                bodyRecords,
                                                userQuery,
                                                trajectory,
                                                scriptRecords,
                                                metadata == null ? Map.of() : new LinkedHashMap<>(metadata)
                                        )))));
    }

    private CompletionStage<Object> generatePreviewOrFailure(String skillName, EvolutionContext context) {
        CompletionStage<LocalApplyPreview> previewStage;
        try {
            previewStage = generateLocalApplyPreview(context);
        } catch (BaseError exception) {
            return CompletableFuture.completedFuture(generationFailed(skillName, exception));
        }
        return previewStage.handle((preview, failure) -> {
            if (failure == null) {
                return preview;
            }
            Throwable cause = unwrap(failure);
            if (cause instanceof BaseError baseError) {
                return generationFailed(skillName, baseError);
            }
            throw new CompletionException(cause);
        });
    }

    private CompletionStage<OnlineEvolutionResult> finishEvolution(
            String skillName,
            LocalApplyPreview preview,
            EvolutionContext onlineContext,
            boolean requiresApproval,
            String source
    ) {
        if (preview.getRecords().isEmpty()) {
            String message = "no applied updates for skill=" + skillName;
            LOGGER.info("[OnlineEvolutionOrchestrator] {}", message);
            return CompletableFuture.completedFuture(result(skillName, "no_evolution_no_records", null, message));
        }

        ExperienceApprovalRequest request = manager.stageApplyResults(
                skillName,
                preview.getApplyResults(),
                requiresApproval,
                source == null ? stageSource : source,
                requestIdPrefix,
                onlineContext.getUserQuery(),
                getSignalType(onlineContext),
                getSignalSource(onlineContext),
                onlineContext.getMessages()
        );
        if (requiresApproval) {
            return CompletableFuture.completedFuture(result(
                    skillName,
                    "staged",
                    request,
                    "evolution request staged for skill=" + skillName
            ));
        }

        String requestId = request == null || request.getRequestId() == null ? "" : request.getRequestId();
        return manager.approveRequest(requestId).thenApply(applyResult -> {
            if (applyResult == null || !applyResult.isOk()) {
                List<String> errors = applyResult == null ? List.of() : applyResult.getErrors();
                String message = errors.isEmpty() ? "persistence failed" : String.join("; ", errors);
                return result(skillName, "persistence_failed", request, message);
            }
            return result(
                    skillName,
                    "auto_approved",
                    request,
                    "evolution request auto-approved for skill=" + skillName
            );
        });
    }

    public static EvolutionSignal getPreferredSignal(EvolutionContext onlineContext) {
        for (EvolutionSignal signal : onlineContext.getSignals()) {
            if (Protocols.USER_INTENT_SIGNAL.equals(signal.getSignalType())) {
                return signal;
            }
        }
        List<EvolutionSignal> signals = onlineContext.getSignals();
        return signals.isEmpty() ? null : signals.get(0);
    }

    public static String getSignalType(EvolutionContext onlineContext) {
        EvolutionSignal preferredSignal = getPreferredSignal(onlineContext);
        return preferredSignal == null ? null : preferredSignal.getSignalType();
    }

    public static String getSignalSource(EvolutionContext onlineContext) {
        EvolutionSignal preferredSignal = getPreferredSignal(onlineContext);
        return preferredSignal == null ? null : EvolutionSignals.getSignalSource(preferredSignal);
    }

    private static OnlineEvolutionResult generationFailed(String skillName, BaseError exception) {
        LOGGER.error(
                "[OnlineEvolutionOrchestrator] generation failed for skill={}: {}",
                skillName,
                exception.getMessage()
        );
        return result(skillName, "generation_failed", null, exception.getMessage());
    }

    private static OnlineEvolutionResult result(
            String skillName,
            String status,
            ExperienceApprovalRequest request,
            String message
    ) {
        return new OnlineEvolutionResult(skillName, status, request, message);
    }

    private static Map<UpdateKey, ?> toUpdateMap(Object rawUpdates) {
        if (!(rawUpdates instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<UpdateKey, Object> updates = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() instanceof UpdateKey updateKey) {
                updates.put(updateKey, entry.getValue());
            }
        }
        return updates;
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure instanceof CompletionException && failure.getCause() != null) {
            return failure.getCause();
        }
        return failure;
    }

    private static <T> List<T> copyList(List<T> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(values);
    }

    private static List<Map<String, Object>> copyMessageList(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> copied = new ArrayList<>();
        for (Map<String, Object> message : messages) {
            copied.add(message == null ? Map.of() : new LinkedHashMap<>(message));
        }
        return List.copyOf(copied);
    }

    /**
     * Minimal typed port over the manager methods used by Python's duck-typed orchestrator.
     */
    public interface ExperienceManagerPort {

        LocalApplyPreview buildLocalApplyPreview(String skillName, List<ApplyResult> applyResults);

        ExperienceApprovalRequest stageApplyResults(
                String skillName,
                List<ApplyResult> applyResults,
                boolean requiresApproval,
                String source,
                String requestIdPrefix,
                String userQuery,
                String signalType,
                String signalSource,
                List<Map<String, Object>> messages
        );

        CompletionStage<ExperienceApplyResult> approveRequest(String requestId);
    }
}
