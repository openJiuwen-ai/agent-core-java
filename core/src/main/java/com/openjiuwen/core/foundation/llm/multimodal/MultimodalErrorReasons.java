/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.multimodal;

/**
 * Structured error reasons of the multimodal content contract.
 *
 * <p>Reasons are fine-grained subdivisions carried in {@code details.reason}
 * of the unified {@code BaseError} mechanism; they do not occupy new numeric
 * status codes.</p>
 *
 * @since 0.1.17
 */
public final class MultimodalErrorReasons {
    /** Content block structure violates the contract. */
    public static final String CONTENT_INVALID = "CONTENT_INVALID";

    /** Image source cannot be resolved into transmittable material. */
    public static final String SOURCE_UNRESOLVED = "SOURCE_UNRESOLVED";

    /** Non-image file block has no available processing chain. */
    public static final String FILE_PROCESSING_REQUIRED = "FILE_PROCESSING_REQUIRED";

    /** Image material cannot be read within the access and budget limits. */
    public static final String IMAGE_LOAD_FAILED = "IMAGE_LOAD_FAILED";

    /** Image material was read but cannot be interpreted as an image. */
    public static final String IMAGE_PROCESS_FAILED = "IMAGE_PROCESS_FAILED";

    /** Pinned material version does not match the stored version. */
    public static final String VERSION_CONFLICT = "VERSION_CONFLICT";

    private MultimodalErrorReasons() {
    }
}
