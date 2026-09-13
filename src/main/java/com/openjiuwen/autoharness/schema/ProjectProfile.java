/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Public class ProjectProfile used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectProfile {
    @Builder.Default
    private String name = "agent-core";
    @Builder.Default
    private String repoUrl = "https://gitcode.com/openJiuwen/agent-core.git";
    @Builder.Default
    private String repoSlug = "openJiuwen/agent-core";
    @Builder.Default
    private String platform = "gitcode";
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @param "openjiuwen/harness/rails/security/prompt_security_rail.py"
     *            "openjiuwen/harness/rails/security/prompt_security_rail.py"
     *            "openjiuwen/harness/rails/security/prompt_security_rail.py"
     *            "openjiuwen/harness/rails/security/prompt_security_rail.py"
     *            "openjiuwen/harness/rails/security/prompt_security_rail.py"
     * @since 0.1.7
     */
    private List<String> immutableFiles = new ArrayList<>(
            List.of("openjiuwen/auto_harness/prompts/identity.md", "openjiuwen/auto_harness/resources/ci_gate.yaml",
                    "openjiuwen/harness/rails/security/prompt_security_rail.py"));
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<String> highImpactPrefixes = new ArrayList<>(List.of("openjiuwen/core/"));
    @Builder.Default
    private String defaultBaseBranch = "develop";
    @Builder.Default
    private String defaultCiProfile = "default";
}
