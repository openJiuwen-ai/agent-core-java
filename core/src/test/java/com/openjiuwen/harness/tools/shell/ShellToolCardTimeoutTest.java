/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.harness.tools.shell.bash.BashTool;
import com.openjiuwen.harness.tools.shell.powershell.PowerShellTool;

import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Shell tool cards must declare {@code resilience.timeout_s} above the tool's own maximum
 * timeout, so the outer call timeout does not cut off commands the tool itself still allows.
 */
class ShellToolCardTimeoutTest {
    @Test
    void bashCardDeclaresCallTimeoutAboveToolMaxTimeout() {
        // Given a bash tool card
        ToolCard card = new BashTool().getCard();

        // When reading the declared outer call timeout
        double declared = declaredCallTimeout(card);

        // Then it exceeds the tool's own maximum timeout of 3600 seconds
        assertThat(declared).isGreaterThan(3600.0D);
    }

    @Test
    void powerShellCardDeclaresCallTimeoutAboveToolMaxTimeout() {
        // Given a PowerShell tool card
        ToolCard card = new PowerShellTool().getCard();

        // When reading the declared outer call timeout
        double declared = declaredCallTimeout(card);

        // Then it exceeds the tool's own maximum timeout of 3600 seconds
        assertThat(declared).isGreaterThan(3600.0D);
    }

    private static double declaredCallTimeout(ToolCard card) {
        Map<String, Object> properties = card.getProperties();
        assertThat(properties).isNotNull();
        Object resilience = properties.get("resilience");
        if (!(resilience instanceof Map<?, ?> resilienceMap)) {
            throw new AssertionError("resilience missing on tool card");
        }
        Object timeout = resilienceMap.get("timeout_s");
        if (!(timeout instanceof Number number)) {
            throw new AssertionError("timeout_s missing on tool card");
        }
        return number.doubleValue();
    }
}
