/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.multimodal.ContentBlock;
import com.openjiuwen.core.foundation.llm.multimodal.MaterialAccessConfig;
import com.openjiuwen.core.foundation.llm.multimodal.MultimodalErrorReasons;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.tools.multimodal.FileSystemImageAssetStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Agent entry acceptance tests of the multimodal minimal scope: structured
 * image/text input through the real {@code invoke} and {@code stream}
 * dispatch point, verified by request capture without a real model.
 */
class MultimodalAgentInputTest {
    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4
    };
    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 5, 6};
    private static final String PNG_DATA_URL =
            "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG_BYTES);
    private static final String JPEG_DATA_URL =
            "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(JPEG_BYTES);

    @TempDir
    Path tempDir;

    @TempDir
    Path outsideDir;

    @Test
    void invoke_stringInput_modelReceivesUnchangedStringContent() {
        CapturingModelClient client = new CapturingModelClient();
        ReActAgent agent = newAgent(client);

        Object result = agent.invoke("hello", new MemorySession());

        assertThat(result).isNotNull();
        UserMessage user = lastUserMessage(client);
        assertThat(user.getContent()).isInstanceOf(String.class);
        assertThat(user.getContent()).isEqualTo("hello");
    }

    @Test
    void invoke_businessMapQuery_modelReceivesUnchangedStringContent() {
        CapturingModelClient client = new CapturingModelClient();
        ReActAgent agent = newAgent(client);

        agent.invoke(Map.of("conversation_id", "biz-session", "query", "hi there"), new MemorySession());

        UserMessage user = lastUserMessage(client);
        assertThat(user.getContent()).isEqualTo("hi there");
    }

    @Test
    void invoke_integerInput_rejectedWithLegacyMessage() {
        CapturingModelClient client = new CapturingModelClient();
        ReActAgent agent = newAgent(client);

        assertThatThrownBy(() -> agent.invoke(42, new MemorySession()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Input must be Map with 'query' or String");
        assertThatThrownBy(() -> agent.invoke(null, new MemorySession()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Input must be Map with 'query' or String");
    }

    @Test
    void invoke_textAndRawImage_requestCarriesOrderedParts() {
        CapturingModelClient client = new CapturingModelClient();
        ReActAgent agent = newAgent(client);
        List<Object> blocks = List.of(
                Map.of("type", "text", "text", "what is in this image"),
                imageBlock("img-1", rawSource(PNG_BYTES),
                        Map.of("description", "first page", "mediaType", "image/png")));

        agent.invoke(blocks, new MemorySession());

        List<Map<String, Object>> parts = lastUserParts(client);
        assertThat(parts).hasSize(3);
        assertThat(parts.get(0)).containsEntry("type", "text").containsEntry("text", "what is in this image");
        assertThat(parts.get(1)).containsEntry("type", "text").containsEntry("text", "first page");
        assertThat(parts.get(2)).containsEntry("type", "image_url");
        assertThat(imageUrlOf(parts.get(2))).containsEntry("url", PNG_DATA_URL);
    }

    @Test
    void stream_textAndRawImage_sharesDispatchWithInvoke() {
        CapturingModelClient client = new CapturingModelClient();
        ReActAgent agent = newAgent(client);
        List<Object> blocks = List.of(
                Map.of("type", "text", "text", "describe"),
                imageBlock("img-1", rawSource(PNG_BYTES), Map.of()));
        MemorySession session = new MemorySession();

        Iterator<Object> iterator = agent.stream(
                Map.of("conversation_id", "stream-session", "query", blocks), session, List.of());
        while (iterator.hasNext()) {
            iterator.next();
        }

        List<Map<String, Object>> parts = lastUserParts(client);
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0)).containsEntry("type", "text").containsEntry("text", "describe");
        assertThat(imageUrlOf(parts.get(1))).containsEntry("url", PNG_DATA_URL);
    }

    @Test
    void invoke_legacyOpenAiDataUrl_reencodedIdentically() {
        CapturingModelClient client = new CapturingModelClient();
        ReActAgent agent = newAgent(client);
        String legacyUrl = "data:image/png;base64,"
                + Base64.getEncoder().encodeToString(PNG_BYTES);
        List<Object> blocks = List.of(
                Map.of("type", "text", "text", "legacy shape"),
                Map.of("type", "image_url", "image_url", Map.of("url", legacyUrl)));

        agent.invoke(blocks, new MemorySession());

        List<Map<String, Object>> parts = lastUserParts(client);
        assertThat(parts).hasSize(2);
        assertThat(imageUrlOf(parts.get(1))).containsEntry("url", PNG_DATA_URL);
    }

    @Test
    void invoke_unknownType_failsContentInvalidWithoutStringFallback() {
        CapturingModelClient client = new CapturingModelClient();
        ReActAgent agent = newAgent(client);
        List<Object> blocks = List.of(Map.of("type", "audio", "id", "snd-1"));

        assertThatThrownBy(() -> agent.invoke(blocks, new MemorySession()))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.CONTENT_INVALID)
                        .containsEntry("blockId", "snd-1")
                        .containsEntry("field", "type"));
        assertThat(client.calls).isEmpty();
    }

    @Test
    void invoke_sourceKindConflict_failsContentInvalid() {
        CapturingModelClient client = new CapturingModelClient();
        ReActAgent agent = newAgent(client);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("kind", "raw");
        source.put("bytes", PNG_BYTES);
        source.put("path", "/workspace/a.png");
        List<Object> blocks = List.of(imageBlock("img-c", source, Map.of()));

        assertThatThrownBy(() -> agent.invoke(blocks, new MemorySession()))
                .isInstanceOfSatisfying(BaseError.class, error -> {
                    assertThat(details(error))
                            .containsEntry("reason", MultimodalErrorReasons.CONTENT_INVALID)
                            .containsEntry("field", "source.path");
                    assertThat(error.getMessage()).contains("only one source kind");
                });
        assertThat(client.calls).isEmpty();
    }

    @Test
    void invoke_nonImageFileBlock_failsFileProcessingRequired() {
        CapturingModelClient client = new CapturingModelClient();
        ReActAgent agent = newAgent(client);
        List<Object> blocks = List.of(fileBlock("file-1", rawSource("%PDF-1.4".getBytes(StandardCharsets.UTF_8)),
                Map.of("mediaType", "application/pdf", "filename", "report.pdf")));

        assertThatThrownBy(() -> agent.invoke(blocks, new MemorySession()))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.FILE_PROCESSING_REQUIRED)
                        .containsEntry("blockId", "file-1")
                        .containsEntry("filename", "report.pdf"));
        assertThat(client.calls).isEmpty();
    }

    @Test
    void invoke_rawPathAssetSources_allEnterRequestInOrder() throws IOException {
        CapturingModelClient client = new CapturingModelClient();
        ReActAgent agent = newAgent(client);
        Path pngFile = tempDir.resolve("a.png");
        Files.write(pngFile, PNG_BYTES);
        FileSystemImageAssetStore store = new FileSystemImageAssetStore(tempDir.resolve("assets"));
        store.put("doc-1", "v1", JPEG_BYTES, "image/jpeg");
        List<Object> blocks = List.of(
                imageBlock("img-raw", rawSource(PNG_BYTES), Map.of()),
                imageBlock("img-path", Map.of("kind", "path", "path", pngFile.toString()), Map.of()),
                imageBlock("img-asset", Map.of("kind", "asset", "assetId", "doc-1", "version", "v1"), Map.of()));

        agent.invoke(blocks, new MemorySession());

        List<Map<String, Object>> parts = lastUserParts(client);
        assertThat(parts).hasSize(3);
        assertThat(imageUrlOf(parts.get(0))).containsEntry("url", PNG_DATA_URL);
        assertThat(imageUrlOf(parts.get(1))).containsEntry("url", PNG_DATA_URL);
        assertThat(imageUrlOf(parts.get(2))).containsEntry("url", JPEG_DATA_URL);
    }

    @Test
    void invoke_urlSource_failsSourceUnresolved() {
        CapturingModelClient client = new CapturingModelClient();
        ReActAgent agent = newAgent(client);
        List<Object> blocks = List.of(imageBlock("img-url",
                Map.of("kind", "url", "url", "https://files.example/b.png"), Map.of()));

        assertThatThrownBy(() -> agent.invoke(blocks, new MemorySession()))
                .isInstanceOfSatisfying(BaseError.class, error -> {
                    assertThat(details(error)).containsEntry("reason",
                            MultimodalErrorReasons.SOURCE_UNRESOLVED);
                    assertThat(error.getMessage()).contains("material blocks failed");
                });
        assertThat(client.calls).isEmpty();
    }

    @Test
    void invoke_assetVersionSwap_doesNotReadNewContent() {
        CapturingModelClient client = new CapturingModelClient();
        ReActAgent agent = newAgent(client);
        FileSystemImageAssetStore store = new FileSystemImageAssetStore(tempDir.resolve("assets"));
        store.put("doc-2", "v1", PNG_BYTES, "image/png");
        List<Object> blocks = List.of(imageBlock("img-a2",
                Map.of("kind", "asset", "assetId", "doc-2", "version", "v1"), Map.of()));

        agent.invoke(blocks, new MemorySession());
        store.put("doc-2", "v2", JPEG_BYTES, "image/jpeg");
        agent.invoke(blocks, new MemorySession());

        assertThat(client.calls).hasSize(2);
        assertThat(imageUrlOf(lastUserParts(client).get(0))).containsEntry("url", PNG_DATA_URL);
    }

    @Test
    void invoke_twentyImagesIncludingDuplicates_allEnterRequestInOrder() {
        CapturingModelClient client = new CapturingModelClient();
        ReActAgent agent = newAgent(client);
        List<Object> blocks = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            byte[] bytes = index % 2 == 0 ? PNG_BYTES : JPEG_BYTES;
            blocks.add(imageBlock("img-" + index, rawSource(bytes), Map.of()));
        }

        agent.invoke(blocks, new MemorySession());

        List<Map<String, Object>> parts = lastUserParts(client);
        assertThat(parts).hasSize(20);
        for (int index = 0; index < 20; index++) {
            String expected = index % 2 == 0 ? PNG_DATA_URL : JPEG_DATA_URL;
            assertThat(imageUrlOf(parts.get(index))).containsEntry("url", expected);
        }
    }

    @Test
    void invoke_brokenImageAmongGood_perItemFailureKeepsOthers() {
        CapturingModelClient client = new CapturingModelClient();
        ReActAgent agent = newAgent(client);
        String missing = tempDir.resolve("missing.png").toString();
        List<Object> blocks = List.of(
                imageBlock("img-good", rawSource(PNG_BYTES), Map.of()),
                imageBlock("img-bad", Map.of("kind", "path", "path", missing), Map.of()));

        agent.invoke(blocks, new MemorySession());

        UserMessage user = lastUserMessage(client);
        List<Map<String, Object>> parts = partsOf(user);
        assertThat(parts).hasSize(2);
        assertThat(imageUrlOf(parts.get(0))).containsEntry("url", PNG_DATA_URL);
        assertThat(parts.get(1)).containsEntry("type", "text");
        assertThat(String.valueOf(parts.get(1).get("text")))
                .contains("img-bad").contains("input index 1")
                .contains(MultimodalErrorReasons.IMAGE_LOAD_FAILED);
        List<Map<String, Object>> itemErrors = itemErrorsOf(user);
        assertThat(itemErrors).hasSize(1);
        assertThat(itemErrors.get(0))
                .containsEntry("index", 1)
                .containsEntry("blockId", "img-bad")
                .containsEntry("reason", MultimodalErrorReasons.IMAGE_LOAD_FAILED);
    }

    @Test
    void invoke_pathOutsideAllowedRoots_denied() throws IOException {
        CapturingModelClient client = new CapturingModelClient();
        ReActAgent agent = newAgent(client);
        Path outside = outsideDir.resolve("secret.png");
        Files.write(outside, PNG_BYTES);
        List<Object> blocks = List.of(imageBlock("img-out",
                Map.of("kind", "path", "path", outside.toString()), Map.of()));

        assertThatThrownBy(() -> agent.invoke(blocks, new MemorySession()))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.SOURCE_UNRESOLVED));
        assertThat(client.calls).isEmpty();
    }

    @Test
    void invoke_baseMessageInput_takesStructuredBranch() {
        CapturingModelClient client = new CapturingModelClient();
        ReActAgent agent = newAgent(client);
        BaseMessage message = new BaseMessage("user", List.of(
                Map.of("type", "text", "text", "from base message"),
                imageBlock("img-bm", rawSource(PNG_BYTES), Map.of())));

        agent.invoke(message, new MemorySession());

        List<Map<String, Object>> parts = lastUserParts(client);
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0)).containsEntry("text", "from base message");
        assertThat(imageUrlOf(parts.get(1))).containsEntry("url", PNG_DATA_URL);
    }

    @Test
    void invoke_structuredQueryInsideBusinessMap_takesStructuredBranch() {
        CapturingModelClient client = new CapturingModelClient();
        ReActAgent agent = newAgent(client);
        List<Object> blocks = List.of(imageBlock("img-q", rawSource(PNG_BYTES), Map.of()));

        agent.invoke(Map.of("conversation_id", "mixed", "query", blocks), new MemorySession());

        assertThat(imageUrlOf(lastUserParts(client).get(0))).containsEntry("url", PNG_DATA_URL);
    }

    @Test
    void invoke_emptyBlockList_failsContentInvalid() {
        CapturingModelClient client = new CapturingModelClient();
        ReActAgent agent = newAgent(client);

        assertThatThrownBy(() -> agent.invoke(List.of(), new MemorySession()))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.CONTENT_INVALID));
        assertThat(client.calls).isEmpty();
    }

    @Test
    void extractUserText_keepsLegacySemanticsBesideBlockExtraction() {
        CapturingModelClient client = new CapturingModelClient();
        ReActAgent agent = newAgent(client);

        assertThat(agent.extractUserText("plain")).isEqualTo("plain");
        assertThat(agent.extractUserText(List.of("a", "b"))).isEqualTo("[a, b]");
        List<ContentBlock> blocks = agent.extractUserContentBlocks(List.of(
                Map.of("type", "text", "text", "hello")));
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0)).isInstanceOf(ContentBlock.Text.class);
    }

    private ReActAgent newAgent(CapturingModelClient client) {
        ReActAgent agent = new ReActAgent(new AgentCard("mm_agent", "mm_agent", "Multimodal agent"));
        MaterialAccessConfig materialAccess = new MaterialAccessConfig();
        materialAccess.setAllowedRoots(List.of(tempDir.toString()));
        materialAccess.setAssetRoot(tempDir.resolve("assets").toString());
        agent.configure(new ReActAgentConfig()
                .configureMaxIterations(3)
                .configureMaterialAccess(materialAccess));
        agent.setLlm(new Model(client));
        return agent;
    }

    private static Map<String, Object> rawSource(byte[] bytes) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("kind", "raw");
        source.put("bytes", bytes);
        return source;
    }

    private static Map<String, Object> imageBlock(String id, Map<String, Object> source,
                                                  Map<String, Object> extraFields) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "image");
        block.put("id", id);
        block.put("source", source);
        block.putAll(extraFields);
        return block;
    }

    private static Map<String, Object> fileBlock(String id, Map<String, Object> source,
                                                 Map<String, Object> extraFields) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "file");
        block.put("id", id);
        block.put("source", source);
        block.putAll(extraFields);
        return block;
    }

    private static UserMessage lastUserMessage(CapturingModelClient client) {
        assertThat(client.calls).isNotEmpty();
        List<BaseMessage> messages = client.calls.get(client.calls.size() - 1);
        UserMessage last = null;
        for (BaseMessage message : messages) {
            if (message instanceof UserMessage user && "user".equals(user.getRole())) {
                last = user;
            }
        }
        assertThat(last).isNotNull();
        return last;
    }

    private static List<Map<String, Object>> lastUserParts(CapturingModelClient client) {
        return partsOf(lastUserMessage(client));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> partsOf(UserMessage message) {
        assertThat(message.getContent()).isInstanceOf(List.class);
        return (List<Map<String, Object>>) message.getContent();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> imageUrlOf(Map<String, Object> part) {
        return (Map<String, Object>) part.get("image_url");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> details(BaseError error) {
        return (Map<String, Object>) error.getDetails();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> itemErrorsOf(UserMessage message) {
        return (List<Map<String, Object>>) message.getMetadata().get("multimodal_item_errors");
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private static final class CapturingModelClient implements Model.ModelClient {
        private final List<List<BaseMessage>> calls = new ArrayList<>();

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            calls.add(List.copyOf(messages));
            return CompletableFuture.completedFuture(new AssistantMessage("ok"));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            calls.add(List.copyOf(messages));
            return new Iterator<>() {
                private boolean isDone;

                @Override
                public boolean hasNext() {
                    return !isDone;
                }

                @Override
                public AssistantMessageChunk next() {
                    isDone = true;
                    return AssistantMessageChunk.builder()
                            .content("ok")
                            .finishReason("stop")
                            .build();
                }
            };
        }
    }

    private static final class MemorySession implements AgentSessionApi, ContextEngine.SessionPort {
        private final String sessionId = unique("session");
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> stream = new ArrayList<>();

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {
            stream.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return stream.iterator();
        }
    }
}
