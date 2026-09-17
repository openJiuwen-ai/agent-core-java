/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.common.eventbus.EventBus;
import com.openjiuwen.core.common.eventbus.EventBusHolder;
import com.openjiuwen.core.common.eventbus.events.ModelRemovedEvent;
import com.openjiuwen.core.common.eventbus.events.ModelUpdatedEvent;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Unified entry point for all model resource operations.
 * <p>
 * This class extends {@link AbstractManager} and serves as the single source of
 * truth for model registration, retrieval, removal, update, default-model
 * management, and dynamic resolution. External callers should interact with
 * models exclusively through this class (or through {@link ResourceMgr} which
 * delegates to it), rather than splitting logic across both classes.
 * <p>
 * Model lifecycle changes (add/update/remove) are published as events via the
 * {@link EventBus}, allowing decoupled listeners (such as
 * {@code ContextProcessorRail}) to react without direct coupling.
 * <p>
 * Mirrors Python's {@code ModelMgr} in {@code resources_manager/model_manager.py}.
 *
 * @since 0.1.7
 */
public class ModelMgr extends AbstractManager<Model> {

    private static final Logger logger = LoggerFactory.getLogger(ModelMgr.class);

    /**
     * The EventBus used to publish model lifecycle events.
     */
    private final EventBus eventBus;

    /**
     * The default model ID. Set via {@link #setDefaultModelId(String)}.
     */
    private volatile String defaultModelId;

    /**
     * Create a ModelMgr that uses the global EventBus from {@link EventBusHolder}.
     *
     * @since 0.1.7
     */
    public ModelMgr() {
        this(EventBusHolder.getInstance());
    }

    /**
     * Create a ModelMgr with a specific EventBus instance.
     *
     * @param eventBus the event bus to use for model change events
     * @since 0.1.16
     */
    public ModelMgr(EventBus eventBus) {
        this.eventBus = eventBus != null ? eventBus : EventBusHolder.getInstance();
    }

    // ── Registration ──────────────────────────────────────────────

    /**
     * Add a model with uniqueness validation.
     *
     * @param modelId model identifier
     * @param model   model supplier
     * @throws IllegalArgumentException if the modelId is already registered
     * @since 0.1.7
     */
    public void addModel(String modelId, Supplier<Model> model) {
        registerResourceProvider(modelId, model);
        eventBus.publish(new ModelUpdatedEvent(modelId));
    }

    /**
     * Update an existing model, or register it if not already present.
     * <p>
     * Unlike {@link #addModel(String, Supplier)}, this method overwrites any
     * existing provider for the given modelId.
     *
     * @param modelId model identifier
     * @param model   model supplier
     * @since 0.1.16
     */
    public void updateModel(String modelId, Supplier<Model> model) {
        providers.put(modelId, model);
        eventBus.publish(new ModelUpdatedEvent(modelId));
        logger.info("model updated: {}", modelId);
    }

    /**
     * Remove a model.
     * <p>
     * Validates that at least one model remains after removal. If the removed
     * model was the default, the first remaining model is automatically set as
     * the new default.
     *
     * @param modelId the model ID to remove
     * @return the removed supplier, or null if not found
     * @throws IllegalArgumentException if removing would leave zero models
     * @since 0.1.7
     */
    public Supplier<? extends Model> removeModel(String modelId) {
        return removeModel(modelId, false);
    }

    /**
     * Remove a model with optional force flag.
     * <p>
     * When {@code force} is {@code false}, validates that at least one model
     * remains after removal. When {@code true}, allows removing the last model
     * (intended for test cleanup). If the removed model was the default, the
     * first remaining model is automatically set as the new default (or
     * {@code null} if none remain and {@code isForce} is {@code true}).
     *
     * @param modelId the model ID to remove
     * @param isForce if {@code true}, allow removing the last model
     * @return the removed supplier, or null if not found
     * @throws IllegalArgumentException if {@code isForce} is {@code false} and
     *         removing would leave zero models
     * @since 0.1.16
     */
    public Supplier<? extends Model> removeModel(String modelId, boolean isForce) {
        // Validate: cannot remove the last model (unless forced)
        if (!isForce && providers.size() <= 1) {
            throw new IllegalArgumentException(
                "cannot remove the last model; at least one model must remain");
        }
        Supplier<? extends Model> removed = unregisterResourceProvider(modelId);
        if (removed != null) {
            // If the default was removed, pick a new default (or clear if none left)
            if (modelId.equals(defaultModelId)) {
                if (!providers.isEmpty()) {
                    String newDefault = providers.keySet().iterator().next();
                    defaultModelId = newDefault;
                    logger.info("default model removed, new default: {}", newDefault);
                } else {
                    defaultModelId = null;
                    logger.info("default model removed, no models remain");
                }
            }
            eventBus.publish(new ModelRemovedEvent(modelId));
            logger.info("model removed: {}", modelId);
        }
        return removed;
    }

    // ── Retrieval ──────────────────────────────────────────────────

    /**
     * Get a model by ID.
     *
     * @param modelId model identifier
     * @return the Model instance, or null if not found
     * @since 0.1.7
     */
    public Model getModel(String modelId) {
        return getResource(modelId);
    }

    /**
     * List all registered model IDs.
     *
     * @return a new list of model IDs
     * @since 0.1.16
     */
    public List<String> listModelIds() {
        return new ArrayList<>(providers.keySet());
    }

    /**
     * Check whether a model with the given ID is registered.
     *
     * @param modelId model identifier
     * @return true if registered
     * @since 0.1.16
     */
    public boolean hasModel(String modelId) {
        return providers.containsKey(modelId);
    }

    // ── Default Model ─────────────────────────────────────────────

    /**
     * Get the default model.
     * <p>
     * If a default model ID has been set, the model for that ID is returned.
     * Otherwise, if at least one model is registered, the first one is returned.
     * If no models are registered, an empty Optional is returned.
     *
     * @return an Optional containing the default Model, or empty if none available
     * @since 0.1.16
     */
    public Optional<Model> getDefaultModel() {
        if (defaultModelId != null) {
            return Optional.ofNullable(getResource(defaultModelId));
        }
        if (providers.isEmpty()) {
            return Optional.empty();
        }
        String firstId = providers.keySet().iterator().next();
        return Optional.ofNullable(getResource(firstId));
    }

    /**
     * Set the default model ID.
     *
     * @param modelId the model ID to set as default; must be registered
     * @throws IllegalArgumentException if the modelId is not registered
     * @since 0.1.16
     */
    public void setDefaultModelId(String modelId) {
        if (modelId != null && !providers.containsKey(modelId)) {
            throw new IllegalArgumentException("model not found: " + modelId);
        }
        this.defaultModelId = modelId;
    }

    /**
     * Get the default model ID.
     *
     * @return the default model ID, or null if not set
     * @since 0.1.16
     */
    public String getDefaultModelId() {
        return defaultModelId;
    }

    // ── Unified Resolution ─────────────────────────────────────────

    /**
     * Unified model resolution with priority: dynamicModelId > fallback config > defaultModel.
     * <p>
     * Resolution order:
     * <ol>
     * <li>If {@code dynamicModelId} is non-blank and registered, return that model.</li>
     * <li>Otherwise, if both fallback configs are non-null, construct a new Model
     *     from the provided config (preserves caller-supplied config semantics).</li>
     * <li>Otherwise, return the default model if available.</li>
     * <li>If none succeed, throw {@link IllegalStateException}.</li>
     * </ol>
     *
     * @param dynamicModelId       the dynamic model ID for this request, may be null
     * @param fallbackClientConfig fallback connection config
     * @param fallbackRequestConfig fallback request config
     * @return the resolved Model instance
     * @throws IllegalStateException if no model can be resolved
     * @since 0.1.16
     */
    public Model resolveModel(String dynamicModelId,
                              ModelClientConfig fallbackClientConfig,
                              ModelRequestConfig fallbackRequestConfig) {
        // 1. Try dynamic model
        if (dynamicModelId != null && !dynamicModelId.isBlank()) {
            Model dynamicModel = getModel(dynamicModelId);
            if (dynamicModel != null) {
                return dynamicModel;
            }
            // Dynamic model not found, fall through to fallback config
        }

        // 2. Fallback: construct from config (preserves caller-supplied config semantics)
        if (fallbackClientConfig != null && fallbackRequestConfig != null) {
            return new Model(fallbackClientConfig, fallbackRequestConfig);
        }

        // 3. Try default model (only when no fallback config is provided)
        Optional<Model> defaultModel = getDefaultModel();
        if (defaultModel.isPresent()) {
            return defaultModel.get();
        }

        throw new IllegalStateException("no model available: dynamic model not found ("
            + dynamicModelId + "), no fallback config provided, and no default model registered");
    }
}
