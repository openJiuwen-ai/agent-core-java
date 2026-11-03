/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.multimodal;

import java.util.Arrays;
import java.util.Objects;

/**
 * Material produced by an {@link ImageMaterialProvider} for one source.
 *
 * <p>The byte array is owned by the caller after resolution and is treated as
 * read-only; it is not copied again for performance. {@code mediaType} is the
 * effective MIME type judged from the actual bytes, falling back to an
 * explicit declaration when the bytes are not recognizable.</p>
 *
 * @param bytes material bytes, never {@code null}
 * @param mediaType effective MIME type, never {@code null} or blank
 * @param sourceId stable source identifier used in error details
 * @param version pinned version of the material; may be {@code null}
 * @since 0.1.17
 */
public record ResolvedImage(byte[] bytes, String mediaType, String sourceId, String version) {

    /**
     * Create a resolved image; {@code null} bytes become an empty array.
     *
     * @param bytes material bytes; may be {@code null}
     * @param mediaType effective MIME type
     * @param sourceId stable source identifier
     * @param version pinned version; may be {@code null}
     */
    public ResolvedImage {
        bytes = bytes == null ? new byte[0] : bytes;
    }

    /**
     * Compare by bytes, media type, source id and version.
     *
     * @param other object to compare
     * @return true when the other object is a resolved image with equal fields
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof ResolvedImage image
                && Arrays.equals(bytes, image.bytes)
                && Objects.equals(mediaType, image.mediaType)
                && Objects.equals(sourceId, image.sourceId)
                && Objects.equals(version, image.version);
    }

    /**
     * Hash consistently with {@link #equals(Object)}.
     *
     * @return hash of bytes, media type, source id and version
     */
    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(bytes) + Objects.hash(mediaType, sourceId, version);
    }
}