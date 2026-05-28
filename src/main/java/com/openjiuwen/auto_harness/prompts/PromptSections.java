/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.prompts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto Harness prompt section builder.
 *
 * <p>Mirrors Python's {@code build_auto_harness_sections} in {@code openjiuwen.auto_harness.prompts.sections}.</p>
 */
public class PromptSections {

    private static final String IDENTITY_PATH = "identity.md";

    /**
     * Load identity.md content.
     *
     * @param promptsDir the prompts directory
     * @return the identity text
     */
    public static String loadIdentity(String promptsDir) {
        Path identityPath = Path.of(promptsDir, IDENTITY_PATH);
        try {
            return Files.readString(identityPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Build Auto Harness Agent's prompt sections.
     *
     * @param ciGateRules CI gate rules text
     * @param wisdom      Experience library synthesized context
     * @param promptsDir  The prompts directory path
     * @return List of PromptSection-like maps
     */
    public static List<Map<String, Object>> buildAutoHarnessSections(
            String ciGateRules,
            String wisdom,
            String promptsDir
    ) {
        List<Map<String, Object>> sections = new ArrayList<>();

        // Identity section (highest priority)
        String identityText = loadIdentity(promptsDir);
        Map<String, Object> identitySection = new HashMap<>();
        identitySection.put("name", "auto_harness_identity");
        identitySection.put("content", Map.of("cn", identityText, "en", identityText));
        identitySection.put("priority", 10);
        sections.add(identitySection);

        // CI Gate rules
        if (ciGateRules != null && !ciGateRules.isEmpty()) {
            Map<String, Object> ciSection = new HashMap<>();
            ciSection.put("name", "auto_harness_ci_gate");
            ciSection.put("content", Map.of(
                    "cn", "## CI 门控规则\n\n" + ciGateRules,
                    "en", "## CI Gate Rules\n\n" + ciGateRules
            ));
            ciSection.put("priority", 20);
            sections.add(ciSection);
        }

        // Experience library context
        if (wisdom != null && !wisdom.isEmpty()) {
            Map<String, Object> wisdomSection = new HashMap<>();
            wisdomSection.put("name", "auto_harness_wisdom");
            wisdomSection.put("content", Map.of(
                    "cn", "## 经验库\n\n" + wisdom,
                    "en", "## Experience Library\n\n" + wisdom
            ));
            wisdomSection.put("priority", 30);
            sections.add(wisdomSection);
        }

        return sections;
    }
}