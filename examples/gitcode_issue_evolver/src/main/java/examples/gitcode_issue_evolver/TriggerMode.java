/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver;

import java.util.Locale;

/**
 * Supported GitCode event admission modes.
 *
 * @since 0.1.12
 */
public enum TriggerMode {
    WEBHOOK,
    POLLING,
    BOTH;

    /**
     * Parse a configuration value while preserving the historical webhook default.
     *
     * @param value configured value
     * @return parsed trigger mode
     * @throws IllegalArgumentException when the value is unsupported
     */
    public static TriggerMode parse(String value) {
        String normalized = value == null || value.isBlank()
                ? WEBHOOK.name() : value.strip().toUpperCase(Locale.ROOT);
        try {
            return TriggerMode.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("triggerMode must be webhook, polling, or both", ex);
        }
    }

    /** @return whether signed webhook admission is enabled */
    public boolean usesWebhook() {
        return this == WEBHOOK || this == BOTH;
    }

    /** @return whether periodic Issue and PR polling is enabled */
    public boolean usesPolling() {
        return this == POLLING || this == BOTH;
    }
}
