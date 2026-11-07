/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.director;

import java.util.List;

/**
 * One shot/clip spec.
 *
 * @param clipIndex 1-based index
 * @param title short beat title
 * @param action action description
 * @param camera camera description
 * @param onScreen on-screen character ids
 * @param settingId scene setting id
 * @param clipPrompt video prompt
 * @param timeline timeline label
 * @since 2026-11-06
 */
public record ClipSpec(
        int clipIndex,
        String title,
        String action,
        String camera,
        List<String> onScreen,
        String settingId,
        String clipPrompt,
        String timeline) {

    public ClipSpec {
        title = title == null ? "" : title.strip();
        action = action == null ? "" : action.strip();
        camera = camera == null ? "" : camera.strip();
        onScreen = onScreen == null ? List.of() : List.copyOf(onScreen);
        settingId = settingId == null || settingId.isBlank() ? "set_1" : settingId.strip();
        clipPrompt = clipPrompt == null ? "" : clipPrompt.strip();
        timeline = timeline == null ? "" : timeline.strip();
    }
}
