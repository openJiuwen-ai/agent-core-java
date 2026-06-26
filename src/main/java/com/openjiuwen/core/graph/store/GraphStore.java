/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.store;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.events.LogEventType;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Logging decorator for graph-state persistence.
 * <p>
 * Mirrors Python's {@code GraphStore} in
 * {@code openjiuwen/core/graph/store/base.py}.
 */
public class GraphStore implements Store {

    private static final LoggerProtocol GRAPH_LOGGER = Loggers.GRAPH;

    private final Store saver;

    public GraphStore(Store saver) {
        this.saver = saver;
    }

    @Override
    public CompletionStage<Optional<GraphStoreState>> get(String sessionId, String ns) {
        try {
            return saver.get(sessionId, ns).handle((state, error) -> {
                if (error != null) {
                    GRAPH_LOGGER.error(
                            "Failed to get graph state, eventType={}, sessionId={}, graphId={}",
                            LogEventType.GRAPH_STORE_GET.getValue(),
                            sessionId,
                            ns
                    );
                    throw propagate(error);
                }
                if (state == null || state.isEmpty()) {
                    GRAPH_LOGGER.debug(
                            "Not found graph state for session, eventType={}, sessionId={}, graphId={}",
                            LogEventType.GRAPH_STORE_GET.getValue(),
                            sessionId,
                            ns
                    );
                    return Optional.empty();
                }
                return state;
            });
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    @Override
    public CompletionStage<Void> save(String sessionId, String ns, GraphStoreState state) {
        GRAPH_LOGGER.debug(
                "Begin to save graph state of super-step[{}], eventType={}, sessionId={}, graphId={}",
                state.getStep(),
                LogEventType.GRAPH_STORE_SAVE.getValue(),
                sessionId,
                ns
        );
        try {
            return saver.save(sessionId, ns, state).handle((ignored, error) -> {
                if (error != null) {
                    GRAPH_LOGGER.error(
                            "Succeed to save graph state of super-step[{}], eventType={}, sessionId={}, graphId={}",
                            state.getStep(),
                            LogEventType.GRAPH_STORE_SAVE.getValue(),
                            sessionId,
                            ns
                    );
                    throw propagate(error);
                }
                GRAPH_LOGGER.debug(
                        "Succeed to save graph state of super-step[{}], eventType={}, sessionId={}, graphId={}",
                        state.getStep(),
                        LogEventType.GRAPH_STORE_SAVE.getValue(),
                        sessionId,
                        ns
                );
                return null;
            });
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    @Override
    public CompletionStage<Void> delete(String sessionId, String ns) {
        String graphId = ns != null ? ns : "all";
        GRAPH_LOGGER.debug(
                "Begin to delete {} graph states for session, eventType={}, sessionId={}, graphId={}",
                graphId,
                LogEventType.GRAPH_STORE_DELETE.getValue(),
                sessionId,
                ns
        );
        try {
            return saver.delete(sessionId, ns).handle((ignored, error) -> {
                if (error != null) {
                    GRAPH_LOGGER.debug(
                            "Failed delete {} graph states for session, eventType={}, sessionId={}, graphId={}",
                            graphId,
                            LogEventType.GRAPH_STORE_DELETE.getValue(),
                            sessionId,
                            ns
                    );
                    throw propagate(error);
                }
                GRAPH_LOGGER.debug(
                        "Succeed to delete {} graph states for session, eventType={}, sessionId={}, graphId={}",
                        graphId,
                        LogEventType.GRAPH_STORE_DELETE.getValue(),
                        sessionId,
                        ns
                );
                return null;
            });
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    private static CompletionException propagate(Throwable error) {
        if (error instanceof CompletionException completionException) {
            return completionException;
        }
        return new CompletionException(error);
    }
}
