/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_creator.skills.skill_omni_creation.scripts;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 4 saves approved image blocks to disk.
 * <p>
 * Mirrors Python's {@code openjiuwen/dev_tools/skill_creator/skills/skill_omni_creation/scripts/stage_04_save.py}.
 */
public final class Stage04Save {

    private Stage04Save() {
    }

    public static List<Map<String, Object>> saveImageBlocks(
            List<Map<String, Object>> blocks,
            Map<String, SkillOmniCommon.AssetPayload> fetched,
            Path imageDir
    ) throws IOException {
        Files.createDirectories(imageDir);
        List<Map<String, Object>> result = new ArrayList<>();
        int counter = 0;
        for (Map<String, Object> block : blocks) {
            if (!"image".equals(block.get("type"))) {
                result.add(block);
                continue;
            }
            String url = String.valueOf(block.get("url"));
            SkillOmniCommon.AssetPayload payload = fetched.get(url);
            if (payload == null) {
                continue;
            }
            String ext = extensionFromUrl(url);
            if (!SkillOmniCommon.SUPPORTED_EXTS.contains(ext)) {
                ext = SkillOmniCommon.MIME_TO_EXT.getOrDefault(payload.mime(), ".png");
            }
            Path destination = imageDir.resolve(String.format("img_%02d%s", counter, ext));
            Files.write(destination, payload.data());
            Map<String, Object> copy = new LinkedHashMap<>(block);
            copy.put("path", destination);
            result.add(copy);
            counter++;
        }
        return result;
    }

    private static String extensionFromUrl(String url) {
        try {
            String name = Path.of(new URI(url).getPath()).getFileName().toString();
            int index = name.lastIndexOf('.');
            return index >= 0 ? name.substring(index).toLowerCase() : "";
        } catch (Exception ignored) {
            return "";
        }
    }
}
