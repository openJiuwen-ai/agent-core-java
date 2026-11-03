/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_creator.skills.skill_omni_creation.scripts;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stage 2 image download and deduplication.
 * <p>
 * Mirrors Python's {@code openjiuwen/dev_tools/skill_creator/skills/skill_omni_creation/scripts/stage_02_download.py}.
 */
public final class Stage02Download {

    private static Fetcher fetcher = new DefaultFetcher();

    private Stage02Download() {
    }

    public interface Fetcher {
        FetchResponse fetch(String url) throws Exception;
    }

    public record FetchResponse(byte[] data, String mime) {
    }

    public record FetchResult(String url, byte[] data, String mime) {
    }

    public record DownloadResult(List<Map<String, Object>> blocks, Map<String, SkillOmniCommon.AssetPayload> fetched) {
    }

    public static void setFetcher(Fetcher customFetcher) {
        fetcher = customFetcher == null ? new DefaultFetcher() : customFetcher;
    }

    public static FetchResult fetchOne(String url) {
        try {
            FetchResponse response = fetcher.fetch(url);
            String mime = response.mime().split(";", 2)[0].trim();
            if (!SkillOmniCommon.SUPPORTED_MIMES.contains(mime)) {
                return new FetchResult(url, null, null);
            }
            if (response.data().length > SkillOmniCommon.MAX_IMAGE_BYTES) {
                return new FetchResult(url, null, null);
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.data()));
            if (image == null || (image.getWidth() < SkillOmniCommon.MIN_DIMENSION
                    && image.getHeight() < SkillOmniCommon.MIN_DIMENSION)) {
                return new FetchResult(url, null, null);
            }
            return new FetchResult(url, response.data(), mime);
        } catch (Exception ignored) {
            return new FetchResult(url, null, null);
        }
    }

    public static DownloadResult downloadImageBlocks(List<Map<String, Object>> blocks) {
        List<Integer> imageIndices = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            Map<String, Object> block = blocks.get(i);
            if ("image".equals(block.get("type"))) {
                imageIndices.add(i);
                urls.add(String.valueOf(block.get("url")));
            }
        }

        Map<String, SkillOmniCommon.AssetPayload> raw = new LinkedHashMap<>();
        for (String url : urls) {
            FetchResult result = fetchOne(url);
            if (result.data() != null && result.mime() != null) {
                raw.put(url, new SkillOmniCommon.AssetPayload(result.data(), result.mime()));
            }
        }

        Set<String> seenHashes = new LinkedHashSet<>();
        Map<String, SkillOmniCommon.AssetPayload> fetched = new LinkedHashMap<>();
        Set<Integer> validIndices = new LinkedHashSet<>();

        for (int blockIndex : imageIndices) {
            String url = String.valueOf(blocks.get(blockIndex).get("url"));
            SkillOmniCommon.AssetPayload payload = raw.get(url);
            if (payload == null) {
                continue;
            }
            String digest = java.util.HexFormat.of().formatHex(MessageDigestHolder.sha256(payload.data()));
            if (!seenHashes.add(digest)) {
                continue;
            }
            fetched.put(url, payload);
            validIndices.add(blockIndex);
        }

        List<Map<String, Object>> newBlocks = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            Map<String, Object> block = blocks.get(i);
            if (!"image".equals(block.get("type")) || validIndices.contains(i)) {
                newBlocks.add(block);
            }
        }
        return new DownloadResult(newBlocks, fetched);
    }

    private static final class DefaultFetcher implements Fetcher {
        private final HttpClient httpClient = HttpClient.newHttpClient();

        @Override
        public FetchResponse fetch(String url) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", SkillOmniCommon.STEALTH_UA)
                    .header("Referer", "https://www.google.com/")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode());
            }
            String mime = response.headers().firstValue("content-type").orElse("image/jpeg");
            return new FetchResponse(response.body(), mime);
        }
    }

    private static final class MessageDigestHolder {
        private static byte[] sha256(byte[] data) {
            try {
                return MessageDigest.getInstance("SHA-256").digest(data);
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 digest is unavailable", exception);
            }
        }
    }
}
