/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.multimodal;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Version-pinned store of framework-internal image assets.
 *
 * <p>An asset is addressed by {@code (assetId, version)}; a stored version is
 * immutable, so the same id can never silently read new content. The default
 * file-system implementation keeps assets under the {@code assetRoot} that is
 * bound to the execution workspace; the global temporary directory is not an
 * implicit shared library.</p>
 *
 * @since 0.1.17
 */
public interface ImageAssetStore {
    /**
     * Read the exact pinned version of an asset.
     *
     * @param assetId asset identifier, never {@code null} or blank
     * @param version pinned version, never {@code null} or blank
     * @return stored material, or empty when the asset or version is absent
     * @throws com.openjiuwen.core.common.exception.BaseError when identifiers
     *         are unsafe or the stored material cannot be read
     */
    Optional<ResolvedImage> get(String assetId, String version);

    /**
     * Return the stored size of one pinned version without reading it.
     *
     * @param assetId asset identifier, never {@code null} or blank
     * @param version pinned version, never {@code null} or blank
     * @return byte size, or empty when the version is absent or unknown
     */
    default OptionalLong sizeOf(String assetId, String version) {
        return get(assetId, version)
                .map(image -> OptionalLong.of(image.bytes().length))
                .orElse(OptionalLong.empty());
    }

    /**
     * List the stored versions of one asset for conflict details.
     *
     * @param assetId asset identifier, never {@code null} or blank
     * @return sorted version names, empty when the asset is unknown
     */
    List<String> listVersions(String assetId);

    /**
     * Store a new immutable version of an asset.
     *
     * @param assetId asset identifier, never {@code null} or blank
     * @param version pinned version, never {@code null} or blank
     * @param bytes material bytes, never {@code null}
     * @param mediaType declared MIME type; may be {@code null}
     * @throws com.openjiuwen.core.common.exception.BaseError when identifiers
     *         are unsafe, the version already exists, or writing fails
     */
    void put(String assetId, String version, byte[] bytes, String mediaType);
}
