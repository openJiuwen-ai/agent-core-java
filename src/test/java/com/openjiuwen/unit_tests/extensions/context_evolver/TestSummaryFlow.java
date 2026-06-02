/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.extensions.context_evolver;

import com.openjiuwen.extensions.context_evolver.core.config.Config;
import com.openjiuwen.extensions.context_evolver.core.context.ServiceContext;
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/extensions/context_evolver/test_summary_flow.py}.
 */
class TestSummaryFlow {

    private Map<String, Object> configSnapshot;

    @BeforeEach
    void captureState() {
        configSnapshot = Config.snapshot();
        ServiceContext.getInstance().clear();
    }

    @AfterEach
    void restoreState() {
        Config.restore(configSnapshot);
        ServiceContext.getInstance().clear();
    }

    @Test
    @DisplayName("test_rb_summarize_and_retrieve")
    @Tag("level0")
    void testRBSummarizeAndRetrieve() {
        TaskMemoryService service = createService("RB");

        Map<String, Object> summary = service.summarize(
            "test_user_cycle",
            "none",
            "How do I implement caching in Python?",
            List.of(
                Map.of(
                    "query", "How do I implement caching in Python?",
                    "response", "I used functools.lru_cache decorator and verified the speedup.",
                    "feedback", "helpful"
                ),
                Map.of(
                    "query", "What's the best way to cache expensive computations?",
                    "response", "I implemented memoization with lru_cache for pure functions.",
                    "feedback", "helpful"
                )
            )
        ).join();

        assertEquals("success", summary.get("status"));
        assertFalse(((List<?>) summary.get("memory")).isEmpty());

        Map<String, Object> retrieve = service.retrieve(
            "test_user_cycle",
            "How should I implement caching?"
        ).join();

        assertEquals("success", retrieve.get("status"));
        assertFalse(((List<?>) retrieve.get("retrieved_memory")).isEmpty());
        assertFalse(String.valueOf(retrieve.get("memory_string")).isBlank());
    }

    @Test
    @DisplayName("test_reme_summarize_and_retrieve")
    @Tag("level0")
    void testReMeSummarizeAndRetrieve() {
        Config.setValue("TOPK_RETRIEVAL", 10);
        Config.setValue("TOPK_RERANK", 3);
        Config.setValue("LLM_RERANK", true);
        Config.setValue("LLM_REWRITE", true);

        TaskMemoryService service = createService("REME");

        Map<String, Object> summary = service.summarize(
            "test_reme_cycle",
            "parallel",
            "How to work with Spotify API for song data?",
            List.of(failedTrajectory(), successTrajectory(), playlistTrajectory()),
            null,
            List.of(0.5d, 1.0d, 1.0d)
        ).join();

        assertEquals("success", summary.get("status"));
        assertTrue(((List<?>) summary.get("memory")).size() >= 3);

        Map<String, Object> retrieve = service.retrieve(
            "test_reme_cycle",
            "How do I get the most played songs from my Spotify library?"
        ).join();

        assertEquals("success", retrieve.get("status"));
        assertFalse(((List<?>) retrieve.get("retrieved_memory")).isEmpty());
        assertTrue(String.valueOf(retrieve.get("memory_string")).startsWith("For the current query"));
    }

    @Test
    @DisplayName("test_ace_summarize_and_retrieve")
    @Tag("level0")
    void testACESummarizeAndRetrieve() {
        TaskMemoryService service = createService("ACE");

        Map<String, Object> summary = service.summarize(
            "test_ace_cycle",
            "none",
            "Give me a comma-separated list of top Spotify songs.",
            List.of(aceTrajectory())
        ).join();

        assertEquals("success", summary.get("status"));
        assertFalse(((List<?>) summary.get("memory")).isEmpty());

        Map<String, Object> retrieve = service.retrieve(
            "test_ace_cycle",
            "How do I get songs from my Spotify library?"
        ).join();

        assertEquals("success", retrieve.get("status"));
        assertFalse(((List<?>) retrieve.get("retrieved_memory")).isEmpty());
        assertTrue(String.valueOf(retrieve.get("memory_string")).contains("spotify.search_songs"));
    }

    @Test
    @DisplayName("test_refcon_summarize_and_retrieve")
    @Tag("level0")
    void testRefConSummarizeAndRetrieve() {
        Config.setValue("TOPK_RETRIEVAL", 10);
        Config.setValue("TOPK_RERANK", 3);
        Config.setValue("LLM_RERANK", true);
        Config.setValue("LLM_REWRITE", true);

        TaskMemoryService service = createService("REFCON");

        Map<String, Object> summary = service.summarize(
            "test_refcon_cycle",
            "parallel",
            "How do I find Python files in Linux?",
            List.of(refConTrajectoryOne(), refConTrajectoryTwo(), refConTrajectoryThree()),
            null,
            List.of(1.0d, 0.8d, 1.0d)
        ).join();

        assertEquals("success", summary.get("status"));
        assertFalse(((List<?>) summary.get("memory")).isEmpty());

        Map<String, Object> retrieve = service.retrieve(
            "test_refcon_cycle",
            "How do I search for Python files on Linux?"
        ).join();

        assertEquals("success", retrieve.get("status"));
        assertFalse(((List<?>) retrieve.get("retrieved_memory")).isEmpty());
        assertFalse(String.valueOf(retrieve.get("memory_string")).isBlank());
    }

    @Test
    @DisplayName("test_divcon_summarize_and_retrieve")
    @Tag("level0")
    void testDivConSummarizeAndRetrieve() {
        Config.setValue("TOPK_RETRIEVAL", 10);
        Config.setValue("TOPK_RERANK", 3);
        Config.setValue("LLM_RERANK", true);
        Config.setValue("LLM_REWRITE", true);

        TaskMemoryService service = createService("DIVCON");

        Map<String, Object> summary = service.summarize(
            "test_divcon_cycle",
            "none",
            "How do I list Docker containers?",
            List.of(divConTrajectoryOne(), divConTrajectoryTwo(), divConTrajectoryThree()),
            null,
            List.of(1.0d, 1.0d, 0.9d)
        ).join();

        assertEquals("success", summary.get("status"));
        assertFalse(((List<?>) summary.get("memory")).isEmpty());

        Map<String, Object> retrieve = service.retrieve(
            "test_divcon_cycle",
            "How can I view active Docker containers?"
        ).join();

        assertEquals("success", retrieve.get("status"));
        assertFalse(((List<?>) retrieve.get("retrieved_memory")).isEmpty());
        assertFalse(String.valueOf(retrieve.get("memory_string")).isBlank());
    }

    @Test
    @DisplayName("test_algorithm_normalize")
    @Tag("level0")
    void testAlgorithmNormalize() {
        assertEquals("ACE", createService("ACE").getSummaryAlgorithm());
        assertEquals("ReasoningBank", createService("RB").getSummaryAlgorithm());
        assertEquals("ReMe", createService("REME").getSummaryAlgorithm());
        assertEquals("RefCon", createService("REFCON").getSummaryAlgorithm());
        assertEquals("DivCon", createService("DIVCON").getSummaryAlgorithm());
    }

    private TaskMemoryService createService(String algorithm) {
        return new TaskMemoryService(
            "gpt-5.2",
            "text-embedding-3-small",
            null,
            algorithm,
            algorithm
        );
    }

    private static String successTrajectory() {
        return """
USER: How do I get the most played songs from my Spotify library?
ASSISTANT: I'll help you retrieve the most played songs.
ACTION: spotify.search_songs(genre='R&B', sort_by='-play_count', page_limit=10)
OBSERVATION: [{"song_id":88,"title":"Crimson Skies","play_count":995},{"song_id":185,"title":"Silent Sea","play_count":990}]
ASSISTANT: Based on the API response, the most played songs are Crimson Skies and Silent Sea.
""";
    }

    private static String playlistTrajectory() {
        return """
USER: What are the top songs in my Spotify playlists?
ASSISTANT: Let me fetch your playlist library and find the top songs.
ACTION: spotify.show_playlist_library()
OBSERVATION: [{"playlist_id":1,"name":"My Favorites","song_count":25}]
ACTION: spotify.show_playlist(playlist_id=1)
OBSERVATION: [{"song_id":12,"title":"Haunted Memories","play_count":965}]
ASSISTANT: Your top song from playlists is Haunted Memories with 965 plays.
""";
    }

    private static String failedTrajectory() {
        return """
USER: Show me my most listened R&B tracks.
ASSISTANT: I'll search for your most played R&B tracks.
ACTION: spotify.show_song_library(genre='R&B')
OBSERVATION: []
ASSISTANT: I could not find enough play count evidence to answer confidently.
""";
    }

    private static String aceTrajectory() {
        return """
USER: Give me a comma-separated list of top Spotify songs.
ASSISTANT: I'll gather the relevant songs from Spotify.
ACTION: spotify.search_songs(genre='R&B', sort_by='-play_count', page_limit=4)
OBSERVATION: [{"title":"Crimson Skies","play_count":995},{"title":"Silent Sea","play_count":990}]
ASSISTANT: Crimson Skies, Silent Sea
""";
    }

    private static String refConTrajectoryOne() {
        return """
USER: How do I search for files by extension in Linux?
ASSISTANT: I'll help you find files by extension.
ACTION: shell.run(command='find /home -name "*.py" -type f')
OBSERVATION: /home/user/scripts/process.py
/home/user/projects/app.py
ASSISTANT: Found 2 Python files: process.py and app.py.
""";
    }

    private static String refConTrajectoryTwo() {
        return """
USER: How can I locate all Python files on the system?
ASSISTANT: Let me search for Python files using find.
ACTION: shell.run(command='find / -name "*.py" -type f 2>/dev/null')
OBSERVATION: /usr/lib/python3/dist-packages/apt/__init__.py
/home/user/scripts/process.py
ASSISTANT: Found multiple Python files across the system.
""";
    }

    private static String refConTrajectoryThree() {
        return """
USER: Find Python scripts but only in the current user's home directory.
ASSISTANT: I'll narrow the search to the home directory.
ACTION: shell.run(command='find ~ -name "*.py" -type f')
OBSERVATION: /home/user/scripts/process.py
/home/user/projects/app.py
ASSISTANT: Found 2 Python scripts in your home directory.
""";
    }

    private static String divConTrajectoryOne() {
        return """
USER: List all running Docker containers.
ASSISTANT: I'll list all running containers.
ACTION: shell.run(command='docker ps')
OBSERVATION: CONTAINER ID   IMAGE     STATUS
abc123         nginx     Up 2 hours
ASSISTANT: Found 1 running container: nginx.
""";
    }

    private static String divConTrajectoryTwo() {
        return """
USER: Show me Docker containers that are currently active.
ASSISTANT: Let me check for active containers.
ACTION: shell.run(command='docker container ls')
OBSERVATION: CONTAINER ID   IMAGE     STATUS
abc123         nginx     Up 2 hours
def456         redis     Up 30 minutes
ASSISTANT: There are 2 active containers: nginx and redis.
""";
    }

    private static String divConTrajectoryThree() {
        return """
USER: List Docker containers including stopped ones.
ASSISTANT: I'll include stopped containers in the listing.
ACTION: shell.run(command='docker ps -a')
OBSERVATION: CONTAINER ID   IMAGE     STATUS
abc123         nginx     Up 2 hours
def456         redis     Exited (0) 1 day ago
ASSISTANT: Found 2 containers: nginx (running), redis (stopped).
""";
    }
}
