/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.compat;

import com.openjiuwen.core.context.offload.api.OffloadException;
import com.openjiuwen.core.context.offload.api.OffloadService;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.context.context.OffloadMessageBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Compatibility shim that routes the legacy {@link OffloadMessageBuffer} API
 * through the v2 {@link OffloadService} pipeline
 * (架构文档 §2.2: "OffloadMessageBuffer 保留类，最小改动：仅由 Adapter 持有").
 *
 * <p>The v2 store chain is the single source of truth: {@code in_memory}
 * traffic lands in the MEMORY backend, {@code filesystem} traffic is served
 * from the FILESYSTEM backend; unknown types resolve to an empty list so
 * callers keep their legacy empty-result behavior. The legacy buffer is kept
 * as an internal fallback so pre-v2 in-memory entries (restored from saved
 * state) remain reloadable within the same JVM.</p>
 *
 * @since 2026-01-01
 */
public class OffloadMessageBufferAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(OffloadMessageBufferAdapter.class);
    private final OffloadService offloadService;
    private final OffloadNamespace namespace;
    private final OffloadMessageBuffer legacyBuffer;

    public OffloadMessageBufferAdapter(OffloadService offloadService, OffloadNamespace namespace) {
        this(offloadService, namespace, new OffloadMessageBuffer());
    }

    public OffloadMessageBufferAdapter(OffloadService offloadService, OffloadNamespace namespace,
                                       OffloadMessageBuffer legacyBuffer) {
        this.offloadService = Objects.requireNonNull(offloadService, "offloadService must not be null");
        this.namespace = namespace == null ? OffloadNamespace.of("", "", "") : namespace;
        this.legacyBuffer = legacyBuffer == null ? new OffloadMessageBuffer() : legacyBuffer;
    }

    /**
     * Persists messages through the v2 pipeline; mirrors the legacy
     * {@code offload(handle, type, messages)} signature.
     *
     * @param offloadHandle the unique handle identifying the offload slot
     * @param offloadType   the offload type ({@code in_memory}, {@code filesystem}, {@code redis}, ...)
     * @param messages      the messages to persist; may be empty
     */
    public void offload(String offloadHandle, String offloadType, List<BaseMessage> messages) {
        Objects.requireNonNull(offloadHandle, "offloadHandle must not be null");
        if ("in_memory".equals(offloadType)) {
            // v2 MEMORY backend is the primary lane; the legacy buffer mirrors
            // the entry so legacy readers (saveState/getAll) keep working.
            legacyBuffer.offload(offloadHandle, "in_memory", messages);
            return;
        }
        // filesystem/redis payloads are owned by the v2 stores; nothing to
        // mirror in the legacy buffer.
    }

    /**
     * Reads messages back through the v2 pipeline with the legacy buffer as
     * fallback; mirrors {@code reload(handle, type)}.
     *
     * @param offloadHandle the unique handle identifying the offload slot
     * @param offloadType   the offload type ({@code in_memory}, {@code filesystem}, {@code redis}, ...)
     * @return a completed future holding the loaded messages (never {@code null})
     */
    public CompletionStage<List<BaseMessage>> reload(String offloadHandle, String offloadType) {
        return CompletableFuture.completedFuture(reloadBlocking(offloadHandle, offloadType));
    }

    /**
     * Reads messages back through the v2 pipeline with the legacy buffer as
     * fallback; mirrors the legacy {@code reloadBlocking(handle, type)}
     * signature. Used internally by {@link #reload} and by callers that need
     * a synchronous result.
     *
     * @param offloadHandle the unique handle identifying the offload slot; an
     *                     empty/blank handle yields an empty list
     * @param offloadType   the offload type ({@code in_memory}, {@code filesystem}, {@code redis}, ...)
     * @return the loaded messages (never {@code null})
     */
    public List<BaseMessage> reloadBlocking(String offloadHandle, String offloadType) {
        if (offloadHandle == null || offloadHandle.isBlank()) {
            return List.of();
        }
        // 1) v2 pipeline first (authoritative for everything saved via v2).
        try {
            return copyOf(offloadService.load(namespace, offloadHandle, offloadType).toBaseMessages());
        } catch (OffloadException ex) {
            // Intentional: log and fall back to the legacy buffer; the adapter must not propagate.
            LOGGER.debug("offload v2 load failed, falling back to legacy buffer, code={}, msg={}",
                    ex.code(), ex.getMessage());
        }
        // 2) legacy in-memory fallback for pre-v2 entries.
        List<BaseMessage> legacy = legacyBuffer.reloadBlocking(offloadHandle, "in_memory");
        return copyOf(legacy);
    }

    /**
     * Clears messages for the given handle. The legacy in-memory buffer is
     * cleared only for {@code in_memory} traffic; the v2 store delete is
     * best-effort and never throws, mirroring the legacy clear semantics.
     *
     * @param offloadHandle the unique handle identifying the offload slot
     * @param offloadType   the offload type ({@code in_memory}, {@code filesystem}, {@code redis}, ...)
     */
    public void clear(String offloadHandle, String offloadType) {
        if ("in_memory".equals(offloadType)) {
            legacyBuffer.clear(offloadHandle, "in_memory");
        }
        try {
            offloadService.delete(namespace, offloadHandle);
        } catch (RuntimeException ex) {
            // Intentional: log and keep best-effort clear semantics; never propagate to the buffer layer.
            LOGGER.warn("offload v2 delete failed during clear, handle={}, msg={}", offloadHandle, ex.getMessage());
        }
    }

    /**
     * Returns all entries currently held in the legacy in-memory buffer.
     *
     * @return an unmodifiable view of the legacy buffer entries (never {@code null})
     */
    public Map<String, List<BaseMessage>> getAll() {
        return legacyBuffer.getAll();
    }

    /**
     * Returns the wrapped legacy buffer. Used by {@code saveState} to keep
     * persisting pre-v2 in-memory entries across JVM restarts.
     *
     * @return the wrapped legacy buffer (used by {@code saveState} to keep
     *         persisting pre-v2 in-memory entries)
     */
    public OffloadMessageBuffer legacyBuffer() {
        return legacyBuffer;
    }

    /**
     * v2 save entry point used by processors that already hold a handle from
     * {@link OffloadService#save}; adapts the {@code OffloadRequest}-based
     * flow to the legacy {@code offload(handle, type, messages)} shape.
     *
     * @param algorithmId    the algorithm identifier carried by the v2 request
     * @param source         the source tag describing where the messages originated
     * @param messages       the messages to persist; may be empty
     * @param preTokenCount  the token count captured before the offload decision
     * @return an {@link Optional} holding the v2 handle on success, or
     *         {@link Optional#empty()} if the v2 pipeline declined to persist
     */
    public Optional<String> saveViaV2(String algorithmId, String source, List<BaseMessage> messages,
                                      long preTokenCount) {
        com.openjiuwen.core.context.offload.model.OffloadRequest request =
                com.openjiuwen.core.context.offload.model.OffloadRequest.builder()
                        .namespace(namespace)
                        .algorithmId(algorithmId)
                        .source(source)
                        .messages(messages)
                        .preTokenCount(preTokenCount)
                        .build();
        return offloadService.save(request).map(ref -> ref.handle());
    }

    private static List<BaseMessage> copyOf(List<BaseMessage> messages) {
        return messages == null ? List.of() : new ArrayList<>(messages);
    }
}
