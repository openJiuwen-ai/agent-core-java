/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.http;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP response handling configuration.
 * <p>
 * Mirrors Python's {@code HttpResponseHandlingConfig}.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HttpResponseHandlingConfig {
    private HttpResponseFormat responseFormat = HttpResponseFormat.AUTODETECT;

    /**
     * ArrayList<>.
     * 
     * @param 204 204
     * @since 0.1.7
     */
    private List<Integer> responseCodeSuccessCodes = new ArrayList<>(List.of(200, 201, 202, 204));

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<Integer> responseCodeFailureCodes = new ArrayList<>();
    private String responseMode = "full";
    private String responseDataProperty;
    private int maxRedirects = 21;
    private boolean isThrowOnHttpError = true;
}
