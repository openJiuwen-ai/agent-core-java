/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.checkpointer.redis.storage;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.foundation.store.BasedKVStorePipeline;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.extensions.store.kv.RedisStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Redis-based storage for workflow session state.
 *
 * <p>Mirrors Python's {@code WorkflowStorage} in
 * {@code openjiuwen/extensions/checkpointer/redis/storage.py}.</p>
 */
public class WorkflowStorage extends BaseRedisStorage {

    private static final String STATE_BLOBS = "workflow_state_blobs";
    private static final String STATE_BLOBS_DUMP_TYPE = "workflow_state_blobs_dump_type";
    private static final String UPDATE_BLOBS = "workflow_update_blobs";
    private static final String UPDATE_BLOBS_DUMP_TYPE = "workflow_update_blobs_dump_type";
    private static final int KEY_NUMS = 4;

    public WorkflowStorage(RedisStore redisStore, Map<String, Object> ttl) {
        super(redisStore, ttl);
    }

    /**
     * Save workflow session state.
     */
    public CompletableFuture<Void> save(Object session) {
        try {
            BaseSession baseSession = requireSession(session);
            String sessionId = baseSession.sessionId();
            String workflowId = resolveWorkflowId(baseSession);

            BasedKVStorePipeline pipeline = redisStore.pipeline();
            boolean hasOperations = false;

            var stateBlob = serializeState(baseSession.state().getState());
            if (stateBlob != null) {
                String dumpTypeKey = Checkpointer.resolveNsKey(
                        sessionId, Checkpointer.SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS_DUMP_TYPE);
                String blobKey = Checkpointer.resolveNsKey(
                        sessionId, Checkpointer.SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS);
                pipeline.set(dumpTypeKey, stateBlob.type(), ttlSeconds);
                pipeline.set(blobKey, stateBlob.data(), ttlSeconds);
                hasOperations = true;
            } else {
                log.warn("Failed to serialize workflow state for workflow {}, session {}", workflowId, sessionId);
            }

            if (baseSession.state() instanceof WorkflowCommitState workflowState) {
                var updatesBlob = serializeState(workflowState.getUpdates());
                if (updatesBlob != null) {
                    String updatesDumpTypeKey = Checkpointer.resolveNsKey(
                            sessionId, Checkpointer.SESSION_NAMESPACE_WORKFLOW, workflowId, UPDATE_BLOBS_DUMP_TYPE);
                    String updatesBlobKey = Checkpointer.resolveNsKey(
                            sessionId, Checkpointer.SESSION_NAMESPACE_WORKFLOW, workflowId, UPDATE_BLOBS);
                    pipeline.set(updatesDumpTypeKey, updatesBlob.type(), ttlSeconds);
                    pipeline.set(updatesBlobKey, updatesBlob.data(), ttlSeconds);
                    hasOperations = true;
                }
            }

            if (hasOperations) {
                pipeline.execute().join();
            }
            return CompletableFuture.completedFuture(null);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(wrapFailure(throwable));
        }
    }

    /**
     * Recover workflow session state.
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Void> recover(Object session, Object inputs) {
        try {
            BaseSession baseSession = requireSession(session);
            InteractiveInput interactiveInput = asInteractiveInput(inputs);
            String sessionId = baseSession.sessionId();
            String workflowId = resolveWorkflowId(baseSession);

            String stateDumpTypeKey = Checkpointer.resolveNsKey(
                    sessionId, Checkpointer.SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS_DUMP_TYPE);
            String stateBlobKey = Checkpointer.resolveNsKey(
                    sessionId, Checkpointer.SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS);
            String updatesDumpTypeKey = Checkpointer.resolveNsKey(
                    sessionId, Checkpointer.SESSION_NAMESPACE_WORKFLOW, workflowId, UPDATE_BLOBS_DUMP_TYPE);
            String updatesBlobKey = Checkpointer.resolveNsKey(
                    sessionId, Checkpointer.SESSION_NAMESPACE_WORKFLOW, workflowId, UPDATE_BLOBS);

            BasedKVStorePipeline pipeline = redisStore.pipeline();
            pipeline.get(stateDumpTypeKey);
            pipeline.get(stateBlobKey);
            pipeline.get(updatesDumpTypeKey);
            pipeline.get(updatesBlobKey);
            List<Object> results = pipeline.execute().join();

            if (results == null || results.size() != KEY_NUMS) {
                log.warn("Unexpected workflow recovery key count for workflow {}, session {}",
                        workflowId, sessionId);
                return CompletableFuture.completedFuture(null);
            }

            if (results.get(0) != null || results.get(1) != null) {
                try {
                    Object state = deserializeState(results.get(0), results.get(1));
                    if (state instanceof Map<?, ?> stateMap) {
                        baseSession.state().setState((Map<String, Object>) stateMap);
                    } else {
                        throw new IllegalArgumentException("Redis workflow state must be a Map");
                    }
                } finally {
                    refreshTtl(List.of(stateDumpTypeKey, stateBlobKey), "workflow", workflowId).join();
                }
            }

            if (interactiveInput != null) {
                processInteractiveInputs(baseSession, interactiveInput);
            }

            if (results.get(2) != null || results.get(3) != null) {
                try {
                    Object updatesState = deserializeState(results.get(2), results.get(3));
                    if (updatesState instanceof Map<?, ?> updatesMap) {
                        if (baseSession.state() instanceof WorkflowCommitState workflowState) {
                            workflowState.setUpdates((Map<String, Object>) updatesMap);
                        }
                    } else {
                        throw new IllegalArgumentException("Redis workflow updates must be a Map");
                    }
                } finally {
                    refreshTtl(List.of(updatesDumpTypeKey, updatesBlobKey), "workflow-updates", workflowId).join();
                }
            }
            return CompletableFuture.completedFuture(null);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(wrapFailure(throwable));
        }
    }

    /**
     * Clear workflow session state.
     */
    public CompletableFuture<Void> clear(String workflowId, String sessionId) {
        try {
            String stateDumpTypeKey = Checkpointer.resolveNsKey(
                    sessionId, Checkpointer.SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS_DUMP_TYPE);
            String stateBlobKey = Checkpointer.resolveNsKey(
                    sessionId, Checkpointer.SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS);
            String updatesDumpTypeKey = Checkpointer.resolveNsKey(
                    sessionId, Checkpointer.SESSION_NAMESPACE_WORKFLOW, workflowId, UPDATE_BLOBS_DUMP_TYPE);
            String updatesBlobKey = Checkpointer.resolveNsKey(
                    sessionId, Checkpointer.SESSION_NAMESPACE_WORKFLOW, workflowId, UPDATE_BLOBS);
            redisStore.batchDelete(List.of(
                    stateDumpTypeKey, stateBlobKey,
                    updatesDumpTypeKey, updatesBlobKey), null).join();
            return CompletableFuture.completedFuture(null);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(wrapFailure(throwable));
        }
    }

    /**
     * Check if workflow session exists.
     */
    public CompletableFuture<Boolean> exists(Object session) {
        try {
            BaseSession baseSession = requireSession(session);
            String sessionId = baseSession.sessionId();
            String workflowId = resolveWorkflowId(baseSession);

            String stateDumpTypeKey = Checkpointer.resolveNsKey(
                    sessionId, Checkpointer.SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS_DUMP_TYPE);
            String stateBlobKey = Checkpointer.resolveNsKey(
                    sessionId, Checkpointer.SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS);
            String updatesDumpTypeKey = Checkpointer.resolveNsKey(
                    sessionId, Checkpointer.SESSION_NAMESPACE_WORKFLOW, workflowId, UPDATE_BLOBS_DUMP_TYPE);
            String updatesBlobKey = Checkpointer.resolveNsKey(
                    sessionId, Checkpointer.SESSION_NAMESPACE_WORKFLOW, workflowId, UPDATE_BLOBS);

            BasedKVStorePipeline pipeline = redisStore.pipeline();
            pipeline.exists(stateDumpTypeKey);
            pipeline.exists(stateBlobKey);
            pipeline.exists(updatesDumpTypeKey);
            pipeline.exists(updatesBlobKey);
            List<Object> results = pipeline.execute().join();

            boolean exists = results != null
                    && results.size() == KEY_NUMS
                    && keyExists(results.get(0))
                    && keyExists(results.get(1));
            return CompletableFuture.completedFuture(exists);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(wrapFailure(throwable));
        }
    }

    @SuppressWarnings("unchecked")
    private void processInteractiveInputs(BaseSession session, InteractiveInput inputs) {
        if (inputs.getRawInputs() != null) {
            if (session.state() instanceof WorkflowCommitState workflowState) {
                workflowState.updateAndCommitWorkflowState(
                        Map.of(Constant.INTERACTIVE_INPUT, inputs.getRawInputs()));
            }
            return;
        }

        Map<String, Object> userInputs = inputs.getUserInputs();
        if (userInputs == null || userInputs.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : userInputs.entrySet()) {
            NodeSession nodeSession = new NodeSession(session, entry.getKey());
            Object interactiveInput = nodeSession.state().get(Constant.INTERACTIVE_INPUT);
            List<Object> inputList;
            if (interactiveInput instanceof List<?> existingInputs) {
                inputList = new ArrayList<>(existingInputs.size() + 1);
                inputList.addAll((List<Object>) existingInputs);
                inputList.add(entry.getValue());
            } else {
                inputList = List.of(entry.getValue());
            }
            nodeSession.state().update(Map.of(Constant.INTERACTIVE_INPUT, inputList));
        }

        if (session.state() instanceof WorkflowCommitState workflowState) {
            workflowState.commit();
        }
    }

    private String resolveWorkflowId(BaseSession session) {
        try {
            Object workflowId = session.getClass().getMethod("workflowId").invoke(session);
            if (workflowId instanceof String text && !text.isBlank()) {
                return text;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall back to session id when the session does not expose workflowId().
        }
        return session.sessionId();
    }
}
