package com.openjiuwen.core.foundation.llm.output_parsers;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code BaseOutputParser} in
 * {@code openjiuwen/core/foundation/llm/output_parsers/output_parser.py}.
 */
class BaseOutputParserTest {

    @Test
    void parseSupportsAsyncCompletionContract() {
        BaseOutputParser parser = new EchoParser();

        Object result = parser.parse("hello").join();

        assertThat(result).isEqualTo("parsed:hello");
    }

    @Test
    void streamParseYieldsMappedFragments() {
        BaseOutputParser parser = new EchoParser();
        List<Object> outputs = new ArrayList<>();

        Iterator<Object> iterator = parser.streamParse(List.of("a", "b").iterator());
        while (iterator.hasNext()) {
            outputs.add(iterator.next());
        }

        assertThat(outputs).containsExactly("stream:a", "stream:b");
    }

    private static final class EchoParser extends BaseOutputParser {

        @Override
        public CompletableFuture<Object> parse(Object inputs) {
            return CompletableFuture.completedFuture("parsed:" + inputs);
        }

        @Override
        public Iterator<Object> streamParse(Iterator<?> streamingInputs) {
            List<Object> outputs = new ArrayList<>();
            while (streamingInputs.hasNext()) {
                outputs.add("stream:" + streamingInputs.next());
            }
            return outputs.iterator();
        }
    }
}
