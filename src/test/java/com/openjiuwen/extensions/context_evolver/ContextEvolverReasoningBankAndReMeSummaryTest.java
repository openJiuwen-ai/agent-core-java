package com.openjiuwen.extensions.context_evolver;

import com.openjiuwen.extensions.context_evolver.core.config.Config;
import com.openjiuwen.extensions.context_evolver.core.context.ServiceContext;
import com.openjiuwen.extensions.context_evolver.schema.ReMeMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReMeRetrievedMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankRetrievedMemory;
import com.openjiuwen.extensions.context_evolver.schema.RetrieveResponse;
import com.openjiuwen.extensions.context_evolver.schema.SummarizeResponse;
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextEvolverReasoningBankAndReMeSummaryTest {

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
    void reasoningBankSummarizeFallsBackToFeedbackForFailureLabel() {
        TaskMemoryService service = new TaskMemoryService(
            "gpt-5.2",
            "text-embedding-3-small",
            null,
            "RB",
            "RB"
        );

        SummarizeResponse summary = service.summarizeResponse(
            "user-rb-feedback",
            "none",
            "How do I implement caching in Python?",
            List.of(Map.of(
                "query", "How do I implement caching in Python?",
                "response", "I kept retrying the same cache key and never verified the result.",
                "feedback", "harmful"
            ))
        ).join();

        assertEquals("success", summary.getStatus());
        ReasoningBankMemory memory = assertInstanceOf(ReasoningBankMemory.class, summary.getMemory().get(0));
        assertEquals(Boolean.FALSE, memory.getLabel());
        assertFalse(memory.getMemory().isEmpty());
        assertTrue(memory.getMemory().get(0).getContent().contains("avoid repeating it"));
    }

    @Test
    void reasoningBankParallelSummarizeLeavesLabelUnsetAndRetrievesFlattenedItems() {
        TaskMemoryService service = new TaskMemoryService(
            "gpt-5.2",
            "text-embedding-3-small",
            null,
            "RB",
            "RB"
        );

        SummarizeResponse summary = service.summarizeResponse(
            "user-rb-parallel",
            "parallel",
            "How should I query Spotify song data?",
            List.of(successTrajectory(), failedTrajectory()),
            List.of(true, false),
            null
        ).join();

        assertEquals("success", summary.getStatus());
        ReasoningBankMemory memory = assertInstanceOf(ReasoningBankMemory.class, summary.getMemory().get(0));
        assertNull(memory.getLabel());
        assertTrue(memory.getMemory().size() >= 2);

        RetrieveResponse retrieve = service.retrieveResponse(
            "user-rb-parallel",
            "How do I query Spotify song data?"
        ).join();

        assertEquals("success", retrieve.getStatus());
        assertTrue(retrieve.getRetrievedMemory().size() >= 2);
        ReasoningBankRetrievedMemory first = assertInstanceOf(
            ReasoningBankRetrievedMemory.class,
            retrieve.getRetrievedMemory().get(0)
        );
        assertTrue(retrieve.getMemoryString().contains(first.getTitle()));
    }

    @Test
    void reMeSummarizeUsesScoresForSuccessFailureAndComparativeMemories() {
        Config.setValue("TOPK_RETRIEVAL", 10);
        Config.setValue("TOPK_RERANK", 3);
        Config.setValue("LLM_RERANK", true);
        Config.setValue("LLM_REWRITE", true);

        TaskMemoryService service = new TaskMemoryService(
            "gpt-5.2",
            "text-embedding-3-small",
            null,
            "ReMe",
            "ReMe"
        );

        SummarizeResponse summary = service.summarizeResponse(
            "user-reme-score",
            "parallel",
            "How to work with Spotify API for song data?",
            List.of(failedTrajectory(), successTrajectory(), playlistTrajectory()),
            null,
            List.of(0.5d, 1.0d, 1.0d)
        ).join();

        assertEquals("success", summary.getStatus());
        assertTrue(summary.getMemory().size() >= 3);
        assertTrue(hasWhenToUse(summary, "after a low-scoring attempt"));
        assertTrue(hasWhenToUse(summary, "When comparing multiple trajectories"));

        RetrieveResponse retrieve = service.retrieveResponse(
            "user-reme-score",
            "How do I get the most played songs from my Spotify library?"
        ).join();

        assertEquals("success", retrieve.getStatus());
        assertFalse(retrieve.getRetrievedMemory().isEmpty());
        ReMeRetrievedMemory first = assertInstanceOf(ReMeRetrievedMemory.class, retrieve.getRetrievedMemory().get(0));
        assertNotNull(first.getWhenToUse());
        assertTrue(retrieve.getMemoryString().startsWith("For the current query"));
    }

    @Test
    void reMeSummarizeFallsBackToFeedbackWhenScoresAreMissing() {
        TaskMemoryService service = new TaskMemoryService(
            "gpt-5.2",
            "text-embedding-3-small",
            null,
            "ReMe",
            "ReMe"
        );

        SummarizeResponse summary = service.summarizeResponse(
            "user-reme-feedback",
            "none",
            "How do I implement caching in Python?",
            List.of(
                Map.of(
                    "query", "How do I implement caching in Python?",
                    "response", "I used functools.lru_cache and checked the speedup before answering.",
                    "feedback", "helpful"
                ),
                Map.of(
                    "query", "How do I implement caching in Python?",
                    "response", "I kept the broken cache key and never verified the output.",
                    "feedback", "harmful"
                )
            )
        ).join();

        assertEquals("success", summary.getStatus());
        assertTrue(summary.getMemory().size() >= 2);
        assertTrue(hasWhenToUse(summary, "after a low-scoring attempt"));
    }

    private static boolean hasWhenToUse(SummarizeResponse summary, String expectedFragment) {
        for (Object raw : summary.getMemory()) {
            ReMeMemory memory = assertInstanceOf(ReMeMemory.class, raw);
            if (memory.getWhenToUse() != null && memory.getWhenToUse().contains(expectedFragment)) {
                return true;
            }
        }
        return false;
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
}
