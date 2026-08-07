/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.gitcode;

/**
 * Current GitCode Issue supplied to stage Agents as untrusted data.
 *
 * @param iid Issue IID
 * @param title Issue title
 * @param description Issue body
 * @param state current state
 * @param url canonical web URL
 * @since 0.1.12
 */
public record FeatureIssue(long iid, String title, String description, String state, String url) {
    /** @return whether the Issue remains open */
    public boolean isOpen() {
        return "open".equalsIgnoreCase(state) || "opened".equalsIgnoreCase(state);
    }
}
