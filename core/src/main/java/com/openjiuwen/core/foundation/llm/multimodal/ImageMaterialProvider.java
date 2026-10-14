/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.multimodal;

/**
 * Injected capability that resolves one explicit source into transmittable
 * material before the request is sent.
 *
 * <p>The contract lives on the message side (foundation) while the default
 * implementation lives in the image domain of the harness, so foundation
 * never depends on harness. Implementations must enforce access control,
 * byte and total budgets, and the read deadline from the given context, and
 * must judge the MIME type from the actual bytes first.</p>
 *
 * <p>Failures are reported by throwing {@code BaseError} built through
 * {@link MultimodalErrors} with {@code details.reason} set to
 * {@code SOURCE_UNRESOLVED}, {@code IMAGE_LOAD_FAILED},
 * {@code IMAGE_PROCESS_FAILED} or {@code VERSION_CONFLICT} plus locators.
 * A resolved material whose effective MIME type is not an image is returned
 * as-is; the caller decides how a non-image material is treated.</p>
 *
 * @since 0.1.17
 */
public interface ImageMaterialProvider {
    /**
     * Resolve one source into model-transmittable material.
     *
     * @param source explicit source of the block, never {@code null}
     * @param declaredMediaType caller-declared MIME type used only when the
     *                          actual bytes are not recognizable; may be {@code null}
     * @param access per-request access and budget context, never {@code null}
     * @return resolved material with the effective MIME type, never {@code null}
     * @throws com.openjiuwen.core.common.exception.BaseError when the source
     *         cannot be resolved within the contract, access limits or budgets
     */
    ResolvedImage resolve(ImageSource source, String declaredMediaType, MaterialAccessContext access);
}
