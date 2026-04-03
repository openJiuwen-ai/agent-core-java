import com.openjiuwen.core.retrieval.reranker.ChatReranker;

import java.util.List;
import java.util.Map;

/**
 * Java counterpart of the Python chat reranker showcase.
 */
public final class ChatRerankerExample {

    private static final String QUERY = "Hello";
    private static final List<String> DOCUMENTS = List.of("Hi", "Aloha", "bonjour");
    private static final String INSTRUCTION = "greeting in french";

    private ChatRerankerExample() {
    }

    public static void main(String[] args) {
        ExampleOutput.section("Chat Reranker Example");
        ExampleOutput.keyValue("Chat reranker model", RetrievalExampleSupport.resolveStringConfig(
                "CHAT_RERANKER_MODEL",
                RetrievalExampleSupport.rerankerConfig().getModelName()));
        ExampleOutput.keyValue("Query", QUERY);
        ExampleOutput.printCollection("Documents", DOCUMENTS);

        ChatReranker reranker;
        try {
            reranker = new ChatReranker(RetrievalExampleSupport.chatRerankerConfig());
        } catch (RuntimeException ex) {
            ExampleOutput.line("Chat reranker configuration error: %s", ex.getMessage());
            ExampleOutput.line("Set CHAT_RERANKER_YES_NO_IDS to two token ids, for example: 9454,2753");
            return;
        }

        ExampleOutput.subsection("Compatibility check");
        if (!runCompatibilityCheck(reranker)) {
            ExampleOutput.line("Chat reranker compatibility check failed.");
            ExampleOutput.line("The configured service must support chat completion logprobs.");
            return;
        }

        Map<String, Double> defaultScores = reranker.rerankScores(QUERY, DOCUMENTS, Boolean.TRUE, Map.of());
        Map<String, Double> instructedScores = reranker.rerankScores(QUERY, DOCUMENTS, INSTRUCTION, Map.of());

        ExampleOutput.subsection("Default instruction");
        ExampleOutput.printScoredMap(defaultScores);

        ExampleOutput.subsection("Custom instruction: " + INSTRUCTION);
        ExampleOutput.printScoredMap(instructedScores);

        ExampleOutput.subsection("Score deltas");
        for (String document : DOCUMENTS) {
            double defaultScore = defaultScores.getOrDefault(document, 0.0);
            double instructedScore = instructedScores.getOrDefault(document, 0.0);
            ExampleOutput.line(
                    "%-12s default=%.4f custom=%.4f delta=%.4f",
                    document,
                    defaultScore,
                    instructedScore,
                    instructedScore - defaultScore);
        }
    }

    private static boolean runCompatibilityCheck(ChatReranker reranker) {
        try {
            Map<String, Double> result = reranker.rerankScores(QUERY, List.of(DOCUMENTS.getFirst()), Boolean.TRUE, Map.of());
            ExampleOutput.line("Compatibility probe succeeded. Score: %.4f", result.getOrDefault(DOCUMENTS.getFirst(), 0.0));
            return true;
        } catch (RuntimeException ex) {
            ExampleOutput.line("Compatibility probe failed: %s", ex.getMessage());
            return false;
        }
    }
}