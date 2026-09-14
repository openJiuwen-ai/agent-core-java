/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.skill_branch;

/**
 * One reference figure declared in skill markdown.
 *
 * <p>Mirrors Python's {@code SkillImageEntry} in
 * {@code openjiuwen/harness/tools/mobile_gui/skill_branch/manifest.py}.
 */
public record SkillImageEntry(
        String imageId,
        String alt,
        String relPath,
        String absPath
) {
}
