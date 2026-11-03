/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.model;

/**
 * Receipt returned by {@code OffloadService#save} on success.
 *
 * <p>{@code type} is the marker token of the concrete backend (never
 * {@code COMPOSITE}); {@code pathOrKey} is the file path or storage key the
 * payload committed to. Processors render the unified offload marker
 * {@code [[OFFLOAD: handle=..., type=..., path=...]]} from these fields.</p>
 */
public record OffloadRef(String handle, String type, String pathOrKey, OffloadBackend backend) {

    /**
     * Maps a concrete backend to its marker type token (共享约定 4):
     * {@code filesystem | in_memory | redis}.
     *
     * @param backend the backend to map; {@code null} falls back to {@code in_memory}
     * @return the marker type token for the given backend
     */
    public static String markerType(OffloadBackend backend) {
        if (backend == null) {
            return "in_memory";
        }
        return switch (backend) {
            case FILESYSTEM -> "filesystem";
            case REDIS -> "redis";
            case COMPOSITE -> "composite";
            case MEMORY -> "in_memory";
        };
    }
}
