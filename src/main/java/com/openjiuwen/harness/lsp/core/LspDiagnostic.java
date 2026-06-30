/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
/**
 * Public class LspDiagnostic used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class LspDiagnostic {
    private String serverName;
    private String uri;
    private Map<String, Object> diagnostic;
}
