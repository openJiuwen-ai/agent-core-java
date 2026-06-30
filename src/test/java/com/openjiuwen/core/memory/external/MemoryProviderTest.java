package com.openjiuwen.core.memory.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.memory.MemInfo;
import com.openjiuwen.core.memory.MemResult;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void mem0ProviderShouldStoreAndSearchConclusions() throws Exception {
        FakeMem0Api api = new FakeMem0Api();
        Mem0MemoryProvider provider = new Mem0MemoryProvider("key", "u1", "a1", false, "https://api.mem0.ai", api);
        provider.initialize(Map.of());

        provider.handleToolCall("mem0_conclude", Map.of("conclusion", "likes coffee"));
        String response = provider.handleToolCall("mem0_search", Map.of("query", "coffee"));
        Map<?, ?> payload = MAPPER.readValue(response, Map.class);

        assertThat(provider.isInitialized()).isTrue();
        assertThat((Integer) payload.get("count")).isEqualTo(1);
        assertThat(String.valueOf(payload.get("results"))).contains("likes coffee");
    }

    @Test
    void mem0ProviderShouldRequireApiKey() {
        Mem0MemoryProvider provider = new Mem0MemoryProvider();
        assertThatThrownBy(() -> provider.initialize(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API key is required");
    }

    @Test
    void openVikingProviderShouldRememberAndBrowseResources() throws Exception {
        FakeVikingApi api = new FakeVikingApi();
        OpenVikingMemoryProvider provider = new OpenVikingMemoryProvider("http://example.invalid", "", "acc", "user", "agent", api);
        provider.initialize(Map.of("session_id", "s1"));

        provider.handleToolCall("viking_remember", Map.of("content", "user prefers vim"));
        provider.handleToolCall("viking_add_resource", Map.of("url", "https://example.com/doc", "title", "Doc"));
        String search = provider.handleToolCall("viking_search", Map.of("query", "vim"));
        String browse = provider.handleToolCall("viking_browse", Map.of("action", "stat"));

        assertThat(search).contains("results").contains("user prefers vim");
        assertThat(browse).contains("memories").contains("resources").contains("path");
    }

    private static final class FakeMem0Api implements Mem0MemoryProvider.Mem0Api {
        private final List<Map<String, Object>> memories = new ArrayList<>();

        @Override
        public List<Map<String, Object>> getAllMemories(String baseUrl, String apiKey, Map<String, Object> filters) {
            return new ArrayList<>(memories);
        }

        @Override
        public List<Map<String, Object>> searchMemories(String baseUrl, String apiKey, String query, Map<String, Object> filters,
                                                        boolean rerank, int topK) {
            String needle = query.toLowerCase();
            List<Map<String, Object>> results = new ArrayList<>();
            for (Map<String, Object> memory : memories) {
                String text = String.valueOf(memory.getOrDefault("memory", ""));
                if (text.toLowerCase().contains(needle)) {
                    results.add(Map.of("memory", text, "score", 0.95));
                }
                if (results.size() >= topK) {
                    break;
                }
            }
            return results;
        }

        @Override
        public void addMemories(String baseUrl, String apiKey, List<Map<String, Object>> messages,
                                Map<String, Object> scope, boolean infer) {
            for (Map<String, Object> message : messages) {
                memories.add(Map.of("memory", String.valueOf(message.getOrDefault("content", ""))));
            }
        }
    }

    private static final class FakeVikingApi implements OpenVikingMemoryProvider.VikingApi {
        private final List<Map<String, Object>> memories = new ArrayList<>();
        private final List<Map<String, Object>> resources = new ArrayList<>();

        @Override
        public List<Map<String, Object>> search(String endpoint, String apiKey, String account, String user, String agent,
                                                Map<String, Object> payload) {
            String needle = String.valueOf(payload.getOrDefault("query", "")).toLowerCase();
            List<Map<String, Object>> results = new ArrayList<>();
            for (Map<String, Object> memory : memories) {
                String content = String.valueOf(memory.getOrDefault("abstract", ""));
                if (content.toLowerCase().contains(needle)) {
                    results.add(new LinkedHashMap<>(memory));
                }
            }
            for (Map<String, Object> resource : resources) {
                String content = String.valueOf(resource.getOrDefault("abstract", ""));
                if (content.toLowerCase().contains(needle)) {
                    results.add(new LinkedHashMap<>(resource));
                }
            }
            return results;
        }

        @Override
        public Map<String, Object> read(String endpoint, String apiKey, String account, String user, String agent,
                                        String uri, String level) {
            for (Map<String, Object> resource : resources) {
                if (uri.equals(resource.get("uri"))) {
                    return Map.of("uri", uri, "level", level, "content", resource.get("abstract"));
                }
            }
            return Map.of("error", "resource not found");
        }

        @Override
        public Map<String, Object> browse(String endpoint, String apiKey, String account, String user, String agent,
                                          String action, String browsePath) {
            if ("stat".equals(action)) {
                return Map.of("path", browsePath, "memories", memories.size(), "resources", resources.size());
            }
            return Map.of("path", browsePath, "entries", resources);
        }

        @Override
        public void appendSessionMessage(String endpoint, String apiKey, String account, String user, String agent,
                                         String sessionId, String role, String content) {
            memories.add(Map.of("uri", "viking://session/" + sessionId + "/" + role, "type", "memory", "score", 1.0, "abstract", content));
        }

        @Override
        public Map<String, Object> addResource(String endpoint, String apiKey, String account, String user, String agent,
                                               Map<String, Object> payload) {
            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("uri", "viking://resource/" + (resources.size() + 1));
            resource.put("type", "resource");
            resource.put("score", 1.0);
            resource.put("abstract", payload.getOrDefault("path", ""));
            resources.add(resource);
            return Map.of("status", "indexed", "uri", resource.get("uri"));
        }
    }

    @Test
    void openJiuwenProviderShouldFormatSearchAndSyncMessages() throws Exception {
        FakeBackend backend = new FakeBackend();
        OpenJiuwenMemoryProvider provider = new OpenJiuwenMemoryProvider(Map.of(), backend, AgentMemoryConfig.builder().build());
        provider.initialize(Map.of("user_id", "u1", "scope_id", "s1", "session_id", "ss1"));

        String search = provider.handleToolCall("ltm_search", Map.of("query", "project"));
        provider.syncTurn("hello", "world", Map.of());
        String prefetch = provider.prefetch("history", Map.of());

        assertThat(search).contains("project context");
        assertThat(prefetch).contains("summary memory");
        assertThat(backend.addMessagesCalls.get()).isEqualTo(1);
        assertThat(backend.lastMessages).hasSize(2);
    }

    private static final class FakeBackend implements OpenJiuwenMemoryProvider.Backend {
        private final AtomicInteger addMessagesCalls = new AtomicInteger();
        private List<BaseMessage> lastMessages = new ArrayList<>();

        @Override
        public List<MemResult> searchUserMem(String query, int num, String userId, String scopeId, double threshold) {
            return List.of(MemResult.builder()
                    .memInfo(MemInfo.builder().memId("1").content("project context").type(MemoryType.USER_PROFILE).build())
                    .score(0.9)
                    .build());
        }

        @Override
        public List<MemResult> searchUserHistorySummary(String query, int num, String userId, String scopeId, double threshold) {
            return List.of(MemResult.builder()
                    .memInfo(MemInfo.builder().memId("2").content("summary memory").type(MemoryType.SUMMARY).build())
                    .score(0.8)
                    .build());
        }

        @Override
        public void addMessages(List<BaseMessage> messages, AgentMemoryConfig config, String userId, String scopeId, String sessionId) {
            addMessagesCalls.incrementAndGet();
            lastMessages = new ArrayList<>(messages);
        }
    }
}
