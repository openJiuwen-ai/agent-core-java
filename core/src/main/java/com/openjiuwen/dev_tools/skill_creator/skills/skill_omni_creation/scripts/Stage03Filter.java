/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_creator.skills.skill_omni_creation.scripts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stage 3 image filtering helpers.
 * <p>
 * Mirrors Python's {@code openjiuwen/dev_tools/skill_creator/skills/skill_omni_creation/scripts/stage_03_filter.py}.
 */
public final class Stage03Filter {

    private Stage03Filter() {
    }

    public record ImageContext(String heading, String textBefore, String textAfter) {
    }

    public static ImageContext getImageContext(List<Map<String, Object>> blocks, int index) {
        String heading = "";
        String textBefore = "";
        for (int i = index - 1; i >= 0; i--) {
            Map<String, Object> block = blocks.get(i);
            if (heading.isBlank() && "heading".equals(block.get("type"))) {
                heading = String.valueOf(block.get("text"));
            }
            if (textBefore.isBlank() && "text".equals(block.get("type"))) {
                textBefore = String.valueOf(block.get("text"));
            }
            if (!heading.isBlank() && !textBefore.isBlank()) {
                break;
            }
        }

        String textAfter = "";
        for (int i = index + 1; i < blocks.size(); i++) {
            Map<String, Object> block = blocks.get(i);
            if ("text".equals(block.get("type"))) {
                textAfter = String.valueOf(block.get("text"));
                break;
            }
        }
        return new ImageContext(heading, textBefore, textAfter);
    }

    public static List<Boolean> filterBatch(
            SkillOmniCommon.ChatClient client,
            List<FilterItem> batchItems,
            List<SkillOmniCommon.AssetPayload> batchImages,
            String pageTitle
    ) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of(
                "type", "text",
                "text", "The user is looking for screenshots that illustrate how to: \"" + pageTitle + "\"\n\n"
                        + "There are " + batchImages.size() + " images numbered 1 to " + batchImages.size() + ". "
                        + "Reply with ONLY a JSON array of " + batchImages.size() + " strings, each \"KEEP\" or \"SKIP\"."
        ));

        for (int i = 0; i < batchItems.size(); i++) {
            FilterItem item = batchItems.get(i);
            SkillOmniCommon.AssetPayload payload = batchImages.get(i);
            List<String> context = new ArrayList<>();
            if ("subpage".equals(item.block().getOrDefault("source", "main"))) {
                context.add("Source: subpage (apply stricter relevance check)");
            }
            if (!item.heading().isBlank()) {
                context.add("Section heading: " + item.heading());
            }
            if (!item.textBefore().isBlank()) {
                context.add("Context before: " + truncate(item.textBefore(), 200));
            }
            if (!item.textAfter().isBlank()) {
                context.add("Context after: " + truncate(item.textAfter(), 200));
            }
            String text = context.isEmpty() ? "(no text context)" : String.join("\n", context);
            content.add(Map.of("type", "text", "text", "Image " + (i + 1) + ":\n" + text));
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", SkillOmniCommon.encodeB64(payload.data(), payload.mime()))
            ));
        }

        try {
            String response = client.chat(SkillOmniCommon.FILTER_PROMPT, content, 0.0, 128, Map.of());
            List<Object> values = SkillOmniCommon.fromJsonList(SkillOmniCommon.stripJsonFence(response));
            List<Boolean> result = new ArrayList<>();
            for (Object value : values) {
                result.add("KEEP".equalsIgnoreCase(String.valueOf(value)));
            }
            return result;
        } catch (Exception ignored) {
            return java.util.Collections.nCopies(batchImages.size(), true);
        }
    }

    public static List<Map<String, Object>> filterImageBlocks(
            SkillOmniCommon.ChatClient client,
            List<Map<String, Object>> blocks,
            Map<String, SkillOmniCommon.AssetPayload> fetched,
            String pageTitle
    ) throws IOException {
        List<ImageBatchItem> imageItems = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            Map<String, Object> block = blocks.get(i);
            if (!"image".equals(block.get("type"))) {
                continue;
            }
            String url = String.valueOf(block.get("url"));
            if (!fetched.containsKey(url)) {
                continue;
            }
            ImageContext context = getImageContext(blocks, i);
            imageItems.add(new ImageBatchItem(i, block, context.heading(), context.textBefore(), context.textAfter()));
        }

        if (imageItems.isEmpty()) {
            return blocks;
        }

        Map<Integer, Boolean> keepFlags = new LinkedHashMap<>();
        int batchSize = SkillOmniCommon.FILTER_BATCH;
        for (int start = 0; start < imageItems.size(); start += batchSize) {
            int end = Math.min(start + batchSize, imageItems.size());
            List<ImageBatchItem> batch = imageItems.subList(start, end);
            List<FilterItem> items = new ArrayList<>();
            List<SkillOmniCommon.AssetPayload> images = new ArrayList<>();
            for (ImageBatchItem batchItem : batch) {
                items.add(new FilterItem(batchItem.block(), batchItem.heading(), batchItem.textBefore(), batchItem.textAfter()));
                images.add(fetched.get(String.valueOf(batchItem.block().get("url"))));
            }
            List<Boolean> results = filterBatch(client, items, images, pageTitle);
            for (int i = 0; i < batch.size(); i++) {
                keepFlags.put(batch.get(i).blockIndex(), results.get(i));
            }
        }

        Set<Integer> skipIndices = new LinkedHashSet<>();
        for (Map.Entry<Integer, Boolean> entry : keepFlags.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) {
                skipIndices.add(entry.getKey());
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            if (!skipIndices.contains(i)) {
                result.add(blocks.get(i));
            }
        }
        return result;
    }

    public record FilterItem(Map<String, Object> block, String heading, String textBefore, String textAfter) {
    }

    private record ImageBatchItem(
            int blockIndex,
            Map<String, Object> block,
            String heading,
            String textBefore,
            String textAfter
    ) {
    }

    private static String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
