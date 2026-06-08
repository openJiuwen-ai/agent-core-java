package com.openjiuwen.core.retrieval;

import com.openjiuwen.core.retrieval.indexing.processor.Processor;
import com.openjiuwen.core.retrieval.utils.CommonUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessorAndCommonUtilsTest {

    @Test
    void processorAndCommonUtilsPreservePythonBehavior() throws ExecutionException, InterruptedException {
        Processor<String> processor = args -> java.util.concurrent.CompletableFuture.completedFuture(args[0] + ":" + args[1]);

        assertThat(processor.process("chunk", 3).get()).isEqualTo("chunk:3");
        assertThat(CommonUtils.deduplicate(List.of("a", "b", "a"))).containsExactly("a", "b");
        assertThat(CommonUtils.deduplicate(List.of("A", "a", "B"), value -> value.toLowerCase())).containsExactly("A", "B");
        assertThat(CommonUtils.createMilvusAlias("manual", "http://m")).isEqualTo("manual");
        assertThat(CommonUtils.createMilvusAlias(null, "http://m", "user", null)).isEqualTo("kb-http://m-user");
        assertThat(CommonUtils.createMilvusAlias(null, "http://m", "", "secret"))
                .isEqualTo("kb-http://m-5ebe2294ecd0e0f08eab7690d2a6ee69");
    }
}
