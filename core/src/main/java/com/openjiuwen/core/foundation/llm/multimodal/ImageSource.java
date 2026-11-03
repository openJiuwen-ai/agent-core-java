/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.multimodal;

import java.util.Arrays;

/**
 * Explicit material source of an image or file content block.
 *
 * <p>The sealed hierarchy enforces that exactly one source kind exists; type
 * is never guessed from string prefixes. {@link Raw} carries bytes already
 * decoded by the caller (they are never decoded again), {@link Url} is only
 * accepted by the contract and rejected by this release's resolver,
 * {@link PathRef} points into the controlled working directories, and
 * {@link AssetRef} references a version-pinned framework asset.</p>
 *
 * @since 0.1.17
 */
public sealed interface ImageSource {
    /** Wire value of {@code source.kind} for inline bytes. */
    String KIND_RAW = "raw";

    /** Wire value of {@code source.kind} for remote URLs (unsupported in this release). */
    String KIND_URL = "url";

    /** Wire value of {@code source.kind} for controlled local paths. */
    String KIND_PATH = "path";

    /** Wire value of {@code source.kind} for version-pinned framework assets. */
    String KIND_ASSET = "asset";

    /**
     * Return the wire value of the source kind.
     *
     * @return one of {@code raw}, {@code url}, {@code path}, {@code asset}
     */
    String kind();

    /**
     * Return a stable identifier of this source used in error details.
     *
     * @return source identifier, never {@code null}
     */
    String sourceId();

    /**
     * Inline bytes delivered by the caller, for example runtime parts already
     * decoded upstream. The bytes are never base64-decoded again.
     *
     * @param bytes material bytes; defensively copied, treated as read-only
     */
    record Raw(byte[] bytes) implements ImageSource {

        /**
         * Create a raw source with a defensive copy of the given bytes.
         *
         * @param bytes material bytes; {@code null} becomes an empty array
         */
        public Raw {
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public String kind() {
            return KIND_RAW;
        }

        @Override
        public String sourceId() {
            return "raw:inline(" + bytes.length + " bytes)";
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Raw raw && Arrays.equals(bytes, raw.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }

    /**
     * Remote URL source. This release rejects URL sources explicitly with
     * {@code SOURCE_UNRESOLVED}; they are never passed through to a model.
     *
     * @param url remote URL, never {@code null} or blank
     */
    record Url(String url) implements ImageSource {

        @Override
        public String kind() {
            return KIND_URL;
        }

        @Override
        public String sourceId() {
            return url;
        }
    }

    /**
     * Local file source restricted to the controlled working directories.
     *
     * @param path local file path, never {@code null} or blank
     * @param version optional pin in {@code sha256:<hex>} form; {@code null}
     *                when the caller does not pin the content
     */
    record PathRef(String path, String version) implements ImageSource {

        @Override
        public String kind() {
            return KIND_PATH;
        }

        @Override
        public String sourceId() {
            return path;
        }
    }

    /**
     * Version-pinned framework asset reference.
     *
     * @param assetId asset identifier, never {@code null} or blank
     * @param version pinned asset version, never {@code null} or blank
     */
    record AssetRef(String assetId, String version) implements ImageSource {

        @Override
        public String kind() {
            return KIND_ASSET;
        }

        @Override
        public String sourceId() {
            return assetId + "@" + version;
        }
    }
}
