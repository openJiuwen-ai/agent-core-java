package examples.retrieval;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.retrieval.query_rewriter.QueryRewriter;
import examples.utils.SharedExampleApiConfigLoader;

import java.util.List;
import java.util.Map;

/**
 * Java counterpart of the Python query rewriter showcase.
 */
public final class QueryRewriterExample {

    private static final int COMPRESS_RANGE = 4;
    private static final List<DialogueTurn> EXAMPLE_TURNS = List.of(
            new DialogueTurn("user", "What is our project's tech stack?"),
            new DialogueTurn("assistant", "The project uses Python 3.11, FastAPI, LangChain and Chroma. Frontend plans to use React."),
            new DialogueTurn("user", "What about deployment?"),
            new DialogueTurn("assistant", "Deployment uses Docker containers, Kubernetes in production, and GitHub Actions for CI."),
            new DialogueTurn("user", "Does it support multi-tenancy?"),
            new DialogueTurn("assistant", "Multi-tenancy is not implemented yet; a future release may add it."),
            new DialogueTurn("user", "What about logging and monitoring?"),
            new DialogueTurn("assistant", "Logging uses ELK; monitoring uses Prometheus and Grafana."));

    private QueryRewriterExample() {
    }

    public static void main(String[] args) {
        BaseModelClient llmClient = RetrievalExampleSupport.queryRewriterClient();
        ModelContext context = RetrievalExampleSupport.createContext("retrieval_example_qr");
        QueryRewriter rewriter = new QueryRewriter(llmClient, context, COMPRESS_RANGE, "zh");

        ExampleOutput.section("Query Rewriter Example");
        ExampleOutput.keyValue("Provider", RetrievalExampleSupport.resolveStringConfig(
                "QUERY_REWRITER_PROVIDER",
                SharedExampleApiConfigLoader.getModelProvider()));
        ExampleOutput.keyValue("Model", RetrievalExampleSupport.resolveStringConfig(
                "QUERY_REWRITER_MODEL",
                SharedExampleApiConfigLoader.getModelName()));
        ExampleOutput.keyValue("Compress range", COMPRESS_RANGE);

        for (int index = 0; index < EXAMPLE_TURNS.size(); index++) {
            DialogueTurn turn = EXAMPLE_TURNS.get(index);
            if ("user".equals(turn.role())) {
                int turnNumber = index / 2 + 1;
                runRewrite(rewriter, context, turn.content(), "Turn " + turnNumber);
                context.addMessages(new UserMessage(turn.content()));
            } else {
                context.addMessages(new AssistantMessage(turn.content()));
            }
        }

        String finalQuery = "Can you summarize that again?";
        runRewrite(rewriter, context, finalQuery, "Final");

        ExampleOutput.subsection("Final context snapshot");
        ExampleOutput.keyValue("Message count", context.size());
        for (BaseMessage message : context.getMessages()) {
            ExampleOutput.line("%s: %s", message.getRole(), stringify(message.getContent()));
        }
    }

    private static void runRewrite(QueryRewriter rewriter, ModelContext context, String query, String turnLabel) {
        ExampleOutput.subsection(turnLabel);
        ExampleOutput.line("User query: %s", query);

        int messageCountBefore = context.size();
        Map<String, Object> rewritten = rewriter.rewrite(query);
        int messageCountAfter = context.size();

        ExampleOutput.keyValue("before", rewritten.getOrDefault("before", ""));
        ExampleOutput.keyValue("standalone_query", rewritten.getOrDefault("standalone_query", ""));
        ExampleOutput.keyValue("intention", rewritten.getOrDefault("intention", ""));
        ExampleOutput.keyValue("from_history", rewritten.getOrDefault("from_history", ""));
        if (rewritten.containsKey("typo")) {
            ExampleOutput.keyValue("typo", rewritten.get("typo"));
        }
        ExampleOutput.line("Use standalone_query for retrieval, then append the turn back into context.");
        if (messageCountBefore >= COMPRESS_RANGE && messageCountAfter == 1) {
            ExampleOutput.line("Compression triggered: history was replaced by a single system summary.");
        }
    }

    private static String stringify(Object value) {
        if (value == null) {
            return "";
        }
        return value instanceof String stringValue ? stringValue : String.valueOf(value);
    }

    private record DialogueTurn(String role, String content) {
    }
}