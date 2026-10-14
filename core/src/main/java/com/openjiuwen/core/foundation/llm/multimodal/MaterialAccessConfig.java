/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.multimodal;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Incremental configuration of multimodal material access.
 *
 * <p>Exposed as {@code material_access} on the ReAct agent configuration.
 * All fields are optional; {@code null} keeps the framework default so
 * existing configurations serialize and behave unchanged.</p>
 *
 * @since 0.1.17
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MaterialAccessConfig {
    @JsonProperty("allowed_roots")
    @JsonAlias("allowedRoots")
    private List<String> allowedRoots;

    @JsonProperty("asset_root")
    @JsonAlias("assetRoot")
    private String assetRoot;

    @JsonProperty("max_image_bytes")
    @JsonAlias("maxImageBytes")
    private Long maxImageBytes;

    @JsonProperty("max_total_image_bytes")
    @JsonAlias("maxTotalImageBytes")
    private Long maxTotalImageBytes;

    @JsonProperty("read_timeout_millis")
    @JsonAlias("readTimeoutMillis")
    private Long readTimeoutMillis;

    @JsonProperty("image_detail")
    @JsonAlias("imageDetail")
    private String imageDetail;

    /**
     * Return the directories path sources may read from.
     *
     * @return configured allowed roots; {@code null} falls back to the
     *         controlled working directories of the execution environment
     */
    public List<String> getAllowedRoots() {
        return allowedRoots;
    }

    /**
     * Set the directories path sources may read from.
     *
     * @param allowedRoots allowed root directories; {@code null} restores the default
     */
    public void setAllowedRoots(List<String> allowedRoots) {
        this.allowedRoots = allowedRoots;
    }

    /**
     * Return the directory owning version-pinned assets and derived material.
     *
     * @return configured asset root; {@code null} falls back to a directory
     *         bound to the execution workspace
     */
    public String getAssetRoot() {
        return assetRoot;
    }

    /**
     * Set the directory owning version-pinned assets and derived material.
     *
     * @param assetRoot asset root directory; {@code null} restores the default
     */
    public void setAssetRoot(String assetRoot) {
        this.assetRoot = assetRoot;
    }

    /**
     * Return the per-image byte budget.
     *
     * @return configured limit; {@code null} uses
     *         {@link MaterialAccessContext#DEFAULT_MAX_IMAGE_BYTES}
     */
    public Long getMaxImageBytes() {
        return maxImageBytes;
    }

    /**
     * Set the per-image byte budget.
     *
     * @param maxImageBytes positive limit; {@code null} restores the default
     */
    public void setMaxImageBytes(Long maxImageBytes) {
        this.maxImageBytes = maxImageBytes;
    }

    /**
     * Return the per-request total image byte budget.
     *
     * @return configured limit; {@code null} uses
     *         {@link MaterialAccessContext#DEFAULT_MAX_TOTAL_IMAGE_BYTES}
     */
    public Long getMaxTotalImageBytes() {
        return maxTotalImageBytes;
    }

    /**
     * Set the per-request total image byte budget.
     *
     * @param maxTotalImageBytes positive limit; {@code null} restores the default
     */
    public void setMaxTotalImageBytes(Long maxTotalImageBytes) {
        this.maxTotalImageBytes = maxTotalImageBytes;
    }

    /**
     * Return the single material read timeout.
     *
     * @return configured timeout in milliseconds; {@code null} uses
     *         {@link MaterialAccessContext#DEFAULT_READ_TIMEOUT_MILLIS}
     */
    public Long getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    /**
     * Set the single material read timeout.
     *
     * @param readTimeoutMillis timeout in milliseconds; {@code null} restores the default
     */
    public void setReadTimeoutMillis(Long readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    /**
     * Return the Chat Completions {@code image_url.detail} hint applied to
     * entry image parts.
     *
     * @return configured detail such as {@code low}, {@code high} or
     *         {@code auto}; {@code null} omits the field entirely
     */
    public String getImageDetail() {
        return imageDetail;
    }

    /**
     * Set the Chat Completions {@code image_url.detail} hint.
     *
     * @param imageDetail detail value; {@code null} omits the field
     */
    public void setImageDetail(String imageDetail) {
        this.imageDetail = imageDetail;
    }
}
