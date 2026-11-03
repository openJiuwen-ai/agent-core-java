/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import java.util.List;
import java.util.Map;

/**
 * Base class for externally registered workflow handlers.
 *
 * <p>Defines all event methods that workflow handlers must implement. Unlike
 * {@code TraceBaseHandler}, this class does not require {@code StreamWriterManager}
 * or the tracer's {@code SpanManager}. External handlers can freely choose their
 * own span management approach (e.g. an OpenTelemetry {@code Tracer}).</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.core.session.tracer.handler.TraceExtWorkflowHandler}.</p>
 *
 * @since 0.1.7
 */
public abstract class TraceExtWorkflowHandler {
    /** Tracer UUID injected by {@code Tracer.init()} to bridge OTel traces with the tracer UUID. */
    protected String traceId = "";

    /** Session id injected by {@code Tracer.init()} (may be null/blank when unbound). */
    protected String sessionId;

    /**
     * Inject the tracer UUID into this handler.
     *
     * <p>Called by {@code Tracer.init()} to associate extension handlers with the
     * current tracer session. Subclasses can use {@code traceId} to bridge OTel
     * traces with the tracer's UUID.</p>
     *
     * @param traceId the tracer UUID
     * @since 0.1.7
     */
    public void setTraceId(String traceId) {
        this.traceId = traceId != null ? traceId : "";
    }

    /**
     * Inject the session id into this handler.
     *
     * <p>Called by {@code Tracer.init()} alongside {@link #setTraceId(String)}.
     * Subclasses can attach {@code sessionId} as an OTel attribute.</p>
     *
     * @param sessionId the session id, or {@code null}
     * @since 0.1.7
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    // --- Lifecycle events ---

    /**
     * Workflow node call starts.
     *
     * @param invokeId  the invoke ID for this node call
     * @param metadata  workflow/component metadata map; must contain
     *                  {@code "parent_node_id"} when a parent node exists
     * @param inputs    the inputs passed to the node
     * @param shouldSend whether the built-in tracer should stream this event
     * @param sourceIds list of source invoke IDs that feed into this node
     * @since 0.1.7
     */
    public abstract void onCallStart(String invokeId, Map<String, Object> metadata, Object inputs,
                                     boolean shouldSend, List<String> sourceIds);

    /**
     * Workflow node call finishes.
     *
     * @param invokeId the invoke ID for this node call
     * @param outputs  the outputs produced by the node
     * @since 0.1.7
     */
    public abstract void onCallDone(String invokeId, Object outputs);

    // --- Input/output events ---

    /**
     * Before a node is invoked (inputs about to be passed in).
     *
     * @param invokeId          the invoke ID for this node call
     * @param inputs            the inputs about to be passed in
     * @param componentMetadata component-level metadata
     * @param shouldSend        whether the built-in tracer should stream this event
     * @since 0.1.7
     */
    public abstract void onPreInvoke(String invokeId, Object inputs, Map<String, Object> componentMetadata,
                                     boolean shouldSend);

    /**
     * Before a streaming input chunk is consumed.
     *
     * @param invokeId   the invoke ID for this node call
     * @param chunk      the streaming input chunk
     * @param shouldSend whether the built-in tracer should stream this event
     * @since 0.1.7
     */
    public abstract void onPreStream(String invokeId, Object chunk, boolean shouldSend);

    /**
     * On a node invoke (buffer data / handle exception).
     *
     * @param invokeId     the invoke ID for this node call
     * @param onInvokeData incremental data produced during the invoke
     * @param exception    exception thrown by the node, or {@code null} if none
     * @since 0.1.7
     */
    public abstract void onInvoke(String invokeId, Map<String, Object> onInvokeData, Throwable exception);

    /**
     * After a node is invoked (outputs produced).
     *
     * @param invokeId the invoke ID for this node call
     * @param outputs  the outputs produced by the node
     * @param inputs   the original inputs passed to the node
     * @since 0.1.7
     */
    public abstract void onPostInvoke(String invokeId, Object outputs, Object inputs);

    /**
     * After a streaming output chunk is produced.
     *
     * @param invokeId the invoke ID for this node call
     * @param chunk    the streaming output chunk
     * @since 0.1.7
     */
    public abstract void onPostStream(String invokeId, Object chunk);

    // --- Interactive events ---

    /**
     * Interactive input event (e.g. waiting for user input).
     *
     * @param invokeId          the invoke ID for this node call
     * @param inputs            the interactive inputs
     * @param componentMetadata component-level metadata
     * @param shouldSend        whether the built-in tracer should stream this event
     * @since 0.1.7
     */
    public abstract void onInteract(String invokeId, Object inputs, Map<String, Object> componentMetadata,
                                    boolean shouldSend);
}
