/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for offloaded message buffering.
 *
 * <p>Mirrors Python's {@code OffloadMessageBuffer} in
 * {@code openjiuwen/core/context_engine/context/message_buffer.py}.</p>
 */
class OffloadMessageBufferTest {

    @Test
    void offloadReloadClearAndGetAllUseInMemoryStore() {
        OffloadMessageBuffer buffer = new OffloadMessageBuffer();
        BaseMessage message = new BaseMessage("user", "stored");

        buffer.offload("h1", "in_memory", List.of(message));

        assertThat(buffer.reload("h1", "in_memory").toCompletableFuture().join()).containsExactly(message);
        assertThat(buffer.getAll()).containsKey("h1");
        buffer.clear("h1", "in_memory");
        assertThat(buffer.reload("h1", "in_memory").toCompletableFuture().join()).isEmpty();
    }

    @Test
    void reloadUnknownStorageReturnsEmptyList() {
        OffloadMessageBuffer buffer = new OffloadMessageBuffer();

        assertThat(buffer.reload("missing", "unknown").toCompletableFuture().join()).isEmpty();
    }

    @Test
    void filesystemReloadReturnsExactPathMessages(@TempDir Path tempDir) throws IOException {
        Path offloadDir = tempDir.resolve(Path.of("context", "s1_context", "offload"));
        Files.createDirectories(offloadDir);
        Path exact = offloadDir.resolve("handle.json");
        Files.writeString(exact, "{\"messages\":[{\"role\":\"user\",\"content\":\"from file\"}]}");
        OffloadMessageBuffer buffer = new OffloadMessageBuffer();
        buffer.setWorkspaceInfo(tempDir.toString(), "s1");
        buffer.setSysOperation(path -> {
            try {
                return Optional.of(Files.readString(Path.of(path)));
            } catch (IOException ex) {
                return Optional.empty();
            }
        });

        List<BaseMessage> reloaded = buffer.reload("handle", "filesystem").toCompletableFuture().join();

        assertThat(reloaded).extracting(BaseMessage::getContent).containsExactly("from file");
    }

    @Test
    void filesystemReloadPreservesUserMultimodalListContent(@TempDir Path tempDir) throws IOException {
        Path offloadDir = tempDir.resolve(Path.of("context", "s1_context", "offload"));
        Files.createDirectories(offloadDir);
        Path exact = offloadDir.resolve("handle.json");
        Files.writeString(exact, "{\"messages\":[{\"role\":\"user\",\"content\":["
                + "{\"type\":\"text\",\"text\":\"look\"},"
                + "{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/png;base64,aaa\"}}]}]}");
        OffloadMessageBuffer buffer = new OffloadMessageBuffer();
        buffer.setWorkspaceInfo(tempDir.toString(), "s1");
        buffer.setSysOperation(path -> {
            try {
                return Optional.of(Files.readString(Path.of(path)));
            } catch (IOException ex) {
                return Optional.empty();
            }
        });

        List<BaseMessage> reloaded = buffer.reload("handle", "filesystem").toCompletableFuture().join();

        assertThat(reloaded).hasSize(1);
        assertThat(reloaded.get(0).getContent()).isInstanceOf(List.class);
        List<?> parts = (List<?>) reloaded.get(0).getContent();
        assertThat(parts).hasSize(2);
        assertThat(asMap(parts.get(0)))
                .containsEntry("type", "text")
                .containsEntry("text", "look");
        assertThat(asMap(parts.get(1))).containsEntry("type", "image_url");
    }

    @Test
    void filesystemReloadPreservesStructuredMapContent(@TempDir Path tempDir) throws IOException {
        Path offloadDir = tempDir.resolve(Path.of("context", "s1_context", "offload"));
        Files.createDirectories(offloadDir);
        Path exact = offloadDir.resolve("handle.json");
        Files.writeString(exact, "{\"messages\":[{\"role\":\"user\",\"content\":"
                + "{\"type\":\"text\",\"text\":\"structured map\"}}]}");
        OffloadMessageBuffer buffer = new OffloadMessageBuffer();
        buffer.setWorkspaceInfo(tempDir.toString(), "s1");
        buffer.setSysOperation(path -> {
            try {
                return Optional.of(Files.readString(Path.of(path)));
            } catch (IOException ex) {
                return Optional.empty();
            }
        });

        List<BaseMessage> reloaded = buffer.reload("handle", "filesystem").toCompletableFuture().join();

        assertThat(reloaded).hasSize(1);
        assertThat(reloaded.get(0).getContent()).isInstanceOf(Map.class);
        assertThat(asMap(reloaded.get(0).getContent()))
                .containsEntry("type", "text")
                .containsEntry("text", "structured map");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @Test
    void filesystemReloadPathsIncludeExactAndSortedPrefixedFiles(@TempDir Path tempDir) throws IOException {
        Path offloadDir = tempDir.resolve(Path.of("context", "s1_context", "offload"));
        Files.createDirectories(offloadDir);
        Path exact = offloadDir.resolve("handle.json");
        Path prefixedB = offloadDir.resolve("b_handle.json");
        Path prefixedA = offloadDir.resolve("a_handle.json");
        Files.writeString(exact, "{}");
        Files.writeString(prefixedB, "{}");
        Files.writeString(prefixedA, "{}");
        OffloadMessageBuffer buffer = new OffloadMessageBuffer();
        buffer.setWorkspaceInfo(tempDir.toString(), "s1");

        assertThat(buffer.filesystemReloadPaths("handle")).containsExactly(
                exact.toString(),
                prefixedA.toString(),
                prefixedB.toString(),
                "handle"
        );
    }

    @Test
    void constructorAcceptsInitialMessages() {
        BaseMessage message = new BaseMessage("assistant", "old");
        OffloadMessageBuffer buffer = new OffloadMessageBuffer(Map.of("h", List.of(message)));

        assertThat(buffer.getAll()).containsEntry("h", List.of(message));
    }
}
