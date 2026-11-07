/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.director;

import java.util.List;
import java.util.Map;

/**
 * Structured creative analysis produced by the director chain.
 *
 * @param storyName story title
 * @param styleLock visual style lock
 * @param characters character specs
 * @param clips clip specs
 * @param scenes scene specs
 * @since 2026-11-06
 */
public record ScriptAnalysis(
        String storyName,
        Map<String, Object> styleLock,
        List<CharacterSpec> characters,
        List<ClipSpec> clips,
        List<SceneSpec> scenes) {

    public ScriptAnalysis {
        storyName = storyName == null || storyName.isBlank() ? "Untitled" : storyName.strip();
        styleLock = styleLock == null ? Map.of() : Map.copyOf(styleLock);
        characters = characters == null ? List.of() : List.copyOf(characters);
        clips = clips == null ? List.of() : List.copyOf(clips);
        scenes = scenes == null ? List.of() : List.copyOf(scenes);
    }
}
