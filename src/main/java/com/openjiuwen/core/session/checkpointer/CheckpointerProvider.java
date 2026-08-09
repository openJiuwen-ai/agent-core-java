/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import java.util.List;
import java.util.Map;

/**
 * Provider interface for creating checkpointer instances.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} from
 * {@code META-INF/services/com.openjiuwen.core.session.checkpointer.CheckpointerProvider}.
 * Each provider declares which {@code typeName()} it supports, plus optional
 * {@link #aliases()} for historical configuration names.
 * Service adapters can also register providers programmatically via
 * {@link CheckpointerFactory#register(String, CheckpointerProvider)}.</p>
 *
 * @since 0.1.7
 */
public interface CheckpointerProvider {

    /**
     * The checkpointer type name this provider handles (e.g., "in_memory", "persistence").
     *
     * <p>Required for {@link java.util.ServiceLoader} discovery. Providers registered
     * only via {@link CheckpointerFactory#register(String, CheckpointerProvider)} may
     * return an empty string when unused for lookup.</p>
     */
    default String typeName() {
        return "";
    }

    /**
     * Additional lookup names for this provider, such as a legacy {@code type} value.
     *
     * <p>Discovered by {@link CheckpointerFactory} together with {@link #typeName()}.
     * Extension-specific aliases belong here so Core does not hard-code them.</p>
     */
    default List<String> aliases() {
        return List.of();
    }

    /**
     * Create a checkpointer with the given configuration.
     */
    Checkpointer create(Map<String, Object> conf);
}
