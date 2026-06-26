/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.agent_evolving.experience.PendingChange;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Shared approval lifecycle helpers bound to one rail instance.
 * <p>
 * Mirrors Python's {@code EvolutionApprovalRuntime} in
 * {@code openjiuwen/harness/rails/evolution/approval_runtime.py}.
 * </p>
 */
public class EvolutionApprovalRuntime {

    private static final LoggerProtocol LOGGER = Loggers.COMMON;

    private final ApprovalManagerProtocol manager;
    private final PendingApprovalSnapshotStore pendingApprovalSnapshots;

    public EvolutionApprovalRuntime(ApprovalManagerProtocol manager,
                                    PendingApprovalSnapshotStore pendingApprovalSnapshots) {
        this.manager = Objects.requireNonNull(manager, "manager is required");
        this.pendingApprovalSnapshots = Objects.requireNonNull(
                pendingApprovalSnapshots,
                "pendingApprovalSnapshots is required"
        );
    }

    public PendingChange lookupPendingApprovalSnapshot(String requestId, String railName, String actionName) {
        PendingChange pending = pendingApprovalSnapshots.get(requestId);
        if (pending == null) {
            LOGGER.warning(String.format("[%s] %s: unknown request_id=%s", railName, actionName, requestId));
        }
        return pending;
    }

    public CompletionStage<PendingRequestResult> approvePendingRequest(String requestId,
                                                                       String railName,
                                                                       String actionName,
                                                                       List<String> approvedRecordIds) {
        PendingChange pending = lookupPendingApprovalSnapshot(requestId, railName, actionName);
        if (pending == null) {
            return CompletableFuture.completedFuture(new PendingRequestResult(null, null));
        }

        CompletionStage<Object> resultStage = approvedRecordIds == null
                ? manager.approveRequest(requestId)
                : manager.approveRequest(requestId, approvedRecordIds);

        return resultStage.thenApply(result -> {
            int pendingCount = intProperty(result, "pending_count", 0);
            if (pendingCount != 0) {
                int appliedCount = intProperty(result, "applied_count", 0);
                LOGGER.warning(String.format(
                        "[%s] %s partial failure: %d/%d record(s) written for '%s' (request=%s); retry %s to complete",
                        railName,
                        actionName,
                        appliedCount,
                        appliedCount + pendingCount,
                        pending.getSkillName(),
                        requestId,
                        actionName
                ));
            }
            return new PendingRequestResult(pending, result);
        });
    }

    public CompletionStage<PendingRequestResult> approvePendingRequest(String requestId,
                                                                       String railName,
                                                                       String actionName) {
        return approvePendingRequest(requestId, railName, actionName, null);
    }

    public CompletionStage<PendingRequestResult> rejectPendingRequest(String requestId,
                                                                      String railName,
                                                                      String actionName) {
        PendingChange pending = lookupPendingApprovalSnapshot(requestId, railName, actionName);
        if (pending == null) {
            return CompletableFuture.completedFuture(new PendingRequestResult(null, null));
        }
        return manager.rejectRequest(requestId)
                .thenApply(result -> new PendingRequestResult(pending, result));
    }

    public <T> CompletionStage<T> finalizeStagedEvolutionRequest(T request,
                                                                 boolean requiresApproval,
                                                                 Function<T, ?> emitApprovalRequest,
                                                                 Function<T, ?> onAutoApproved) {
        if (request == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (requiresApproval) {
            Object outcome = Objects.requireNonNull(emitApprovalRequest, "emitApprovalRequest is required")
                    .apply(request);
            return waitForOutcome(outcome).thenApply(ignored -> request);
        }

        if (onAutoApproved != null) {
            Object outcome = onAutoApproved.apply(request);
            return waitForOutcome(outcome).thenApply(ignored -> request);
        }
        return CompletableFuture.completedFuture(request);
    }

    public <T> CompletionStage<T> finalizeStagedEvolutionRequest(T request,
                                                                 boolean requiresApproval,
                                                                 Function<T, ?> emitApprovalRequest) {
        return finalizeStagedEvolutionRequest(request, requiresApproval, emitApprovalRequest, null);
    }

    private static CompletionStage<Void> waitForOutcome(Object outcome) {
        if (outcome instanceof CompletionStage<?> stage) {
            return stage.thenApply(ignored -> null);
        }
        return CompletableFuture.completedFuture(null);
    }

    private static int intProperty(Object result, String snakeName, int fallback) {
        if (result == null) {
            return fallback;
        }
        if (result instanceof Map<?, ?> map) {
            Object value = map.containsKey(snakeName) ? map.get(snakeName) : map.get(toCamelCase(snakeName));
            return intValue(value, fallback);
        }
        Object value = invokeNoArg(result, snakeName);
        if (value == null) {
            value = invokeNoArg(result, toCamelCase(snakeName));
        }
        if (value == null) {
            value = invokeNoArg(result, "get" + capitalize(toCamelCase(snakeName)));
        }
        return intValue(value, fallback);
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    private static String toCamelCase(String snakeName) {
        StringBuilder builder = new StringBuilder();
        boolean upperNext = false;
        for (char item : snakeName.toCharArray()) {
            if (item == '_') {
                upperNext = true;
                continue;
            }
            builder.append(upperNext ? String.valueOf(item).toUpperCase(Locale.ROOT) : item);
            upperNext = false;
        }
        return builder.toString();
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    public static final class PendingRequestResult {
        private final PendingChange pending;
        private final Object result;

        public PendingRequestResult(PendingChange pending, Object result) {
            this.pending = pending;
            this.result = result;
        }

        public PendingChange getPending() {
            return pending;
        }

        public Object getResult() {
            return result;
        }
    }
}
