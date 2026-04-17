package com.openjiuwen.extensions.context_evolver;

import com.openjiuwen.extensions.context_evolver.core.config.Config;
import com.openjiuwen.extensions.context_evolver.core.context.ServiceContext;
import com.openjiuwen.extensions.context_evolver.schema.ACERetrievedMemory;
import com.openjiuwen.extensions.context_evolver.schema.PersonalMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReMeRetrievedMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankRetrievedMemory;
import com.openjiuwen.extensions.context_evolver.schema.RetrieveResponse;
import com.openjiuwen.extensions.context_evolver.schema.SchemaUtils;
import com.openjiuwen.extensions.context_evolver.schema.TaskMemory;
import com.openjiuwen.extensions.context_evolver.service.AddMemoryRequest;
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextEvolverServiceAndSchemaTest {

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
    void addMemoryAndRetrieveAceUsesTypedSchemaPayloads() {
        TaskMemoryService service = new TaskMemoryService(
            "gpt-5.2",
            "text-embedding-3-small",
            null,
            "ACE",
            "ACE"
        );

        AddMemoryRequest request = new AddMemoryRequest();
        request.setContent("Use functools.lru_cache decorator for pure functions.");
        request.setSection("python_best_practices");

        Map<String, Object> addResult = service.addMemory("user-ace", request).join();
        assertEquals("success", addResult.get("status"));
        assertEquals("ACE", service.getSummaryAlgorithm());

        RetrieveResponse response = service.retrieveResponse("user-ace", "How do I implement caching in Python?").join();
        assertEquals("success", response.getStatus());
        assertEquals(1, response.getRetrievedMemory().size());
        ACERetrievedMemory memory = assertInstanceOf(ACERetrievedMemory.class, response.getRetrievedMemory().get(0));
        assertEquals("python_best_practices", memory.getSection());
        assertTrue(response.getMemoryString().contains("Section: python_best_practices"));

        Map<String, Object> payload = service.retrieve("user-ace", "How do I implement caching in Python?").join();
        List<?> retrieved = (List<?>) payload.get("retrieved_memory");
        Map<?, ?> first = (Map<?, ?>) retrieved.get(0);
        assertEquals("python_best_practices", first.get("section"));
        assertTrue(String.valueOf(first.get("content")).contains("lru_cache"));
    }

    @Test
    void addMemoryAndRetrieveReasoningBankUsesDescriptionAsDefaultQuery() {
        TaskMemoryService service = new TaskMemoryService(
            "gpt-5.2",
            "text-embedding-3-small",
            null,
            "RB",
            "RB"
        );

        AddMemoryRequest request = new AddMemoryRequest();
        request.setContent("Prefer reusable reasoning patterns over trajectory-specific details.");
        request.setTitle("Reusable Strategy");
        request.setDescription("How to distill a reusable reasoning pattern");

        service.addMemory("user-rb", request).join();

        RetrieveResponse response = service.retrieveResponse("user-rb", "How should I generalize a strategy?").join();
        assertEquals(1, response.getRetrievedMemory().size());
        ReasoningBankRetrievedMemory memory = assertInstanceOf(
            ReasoningBankRetrievedMemory.class,
            response.getRetrievedMemory().get(0)
        );
        assertEquals("Reusable Strategy", memory.getTitle());
        assertEquals("How to distill a reusable reasoning pattern", memory.getDescription());

        Map<String, Object> playbook = service.getPlaybook("user-rb").join();
        assertEquals(1, playbook.get("memory_count"));
        Map<?, ?> storedMemory = (Map<?, ?>) ((List<?>) playbook.get("memories")).get(0);
        assertEquals("How to distill a reusable reasoning pattern", storedMemory.get("query"));
        Map<?, ?> storedItem = (Map<?, ?>) ((List<?>) storedMemory.get("memory")).get(0);
        assertEquals("Reusable Strategy", storedItem.get("title"));
    }

    @Test
    void addMemoryAndRetrieveReMeUsesManualMetadataDefaults() {
        TaskMemoryService service = new TaskMemoryService(
            "gpt-5.2",
            "text-embedding-3-small",
            null,
            "ReMe",
            "ReMe"
        );

        AddMemoryRequest request = new AddMemoryRequest();
        request.setContent("Capture the invariant first, then adapt the API details.");
        request.setWhenToUse("When translating a Python API shape into Java");

        service.addMemory("user-reme", request).join();

        RetrieveResponse response = service.retrieveResponse("user-reme", "How should I port an API contract?").join();
        assertEquals(1, response.getRetrievedMemory().size());
        ReMeRetrievedMemory memory = assertInstanceOf(ReMeRetrievedMemory.class, response.getRetrievedMemory().get(0));
        assertEquals("When translating a Python API shape into Java", memory.getWhenToUse());

        Map<String, Object> playbook = service.getPlaybook("user-reme").join();
        Map<?, ?> storedMemory = (Map<?, ?>) ((List<?>) playbook.get("memories")).get(0);
        Map<?, ?> metadata = (Map<?, ?>) storedMemory.get("metadata");
        assertEquals("manual", metadata.get("step_type"));
        assertEquals(1, metadata.get("freq"));
        assertEquals(1.0d, metadata.get("utility"));
    }

    @Test
    void taskAndPersonalMemoryRoundTripPreservesStableMetadata() {
        TaskMemory taskMemory = new TaskMemory();
        taskMemory.setWorkspaceId("workspace-a");
        taskMemory.setContent("Use a dedicated DTO instead of an anonymous map.");
        taskMemory.setWhenToUse("When stabilizing a public service contract");
        taskMemory.setHelpfulCount(3);
        taskMemory.setHarmfulCount(1);
        taskMemory.setSection("api");

        var taskNode = taskMemory.toVectorNode();
        assertEquals(
            "task_workspace-a_" + SchemaUtils.md5Hex("Use a dedicated DTO instead of an anonymous map."),
            taskNode.getId()
        );

        TaskMemory restoredTaskMemory = TaskMemory.fromVectorNode(taskNode);
        assertEquals("api", restoredTaskMemory.getSection());
        assertEquals(2.0d, restoredTaskMemory.getScore());

        PersonalMemory personalMemory = new PersonalMemory();
        personalMemory.setWorkspaceId("workspace-b");
        personalMemory.setContent("This user prefers terse API examples.");
        personalMemory.setTarget("writing style");
        personalMemory.setReflectionSubject("docs");

        PersonalMemory restoredPersonalMemory = PersonalMemory.fromVectorNode(personalMemory.toVectorNode());
        assertEquals("writing style", restoredPersonalMemory.getTarget());
        assertEquals("docs", restoredPersonalMemory.getReflectionSubject());
    }

    @Test
    void clearPlaybookOnlyRemovesRequestedUser() {
        TaskMemoryService service = new TaskMemoryService(
            "gpt-5.2",
            "text-embedding-3-small",
            null,
            "ACE",
            "ACE"
        );

        AddMemoryRequest first = new AddMemoryRequest();
        first.setContent("User A memory");
        first.setSection("test");
        service.addMemory("user-a", first).join();

        AddMemoryRequest second = new AddMemoryRequest();
        second.setContent("User B memory");
        second.setSection("test");
        service.addMemory("user-b", second).join();

        service.clearPlaybook("user-a").join();

        assertEquals(0, service.getPlaybook("user-a").join().get("memory_count"));
        assertEquals(1, service.getPlaybook("user-b").join().get("memory_count"));
    }
}
