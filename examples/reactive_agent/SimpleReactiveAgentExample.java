package examples.reactive_agent;

import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 示例用最简 Agent：不依赖真实 LLM，invoke 返回固定回答，stream 逐词输出 token。
 * 用于验证 Mono/Flux 包装在端到端路径上的正确性。
 */
public class SimpleReactiveAgentExample extends BaseAgent {

    public static final String ID = "demo-agent";

    public SimpleReactiveAgentExample() {
        super(AgentCard.builder()
                .id(ID)
                .name(ID)
                .description("响应式接口示例 Agent（无 LLM 依赖）")
                .build());
    }

    @Override
    public BaseAgent configure(Object config) {
        return this;
    }

    @Override
    public Object getConfig() {
        return null;
    }

    /** 模拟阻塞 LLM 调用（80ms），返回结构化答案。 */
    @Override
    public Object invoke(Object inputs, AgentSession session) {
        sleep(80);
        String query = extractQuery(inputs);
        return Map.of(
                "result_type", "answer",
                "answer", "【Mono 响应】收到：「" + query + "」",
                "thread", Thread.currentThread().getName()
        );
    }

    /** 模拟流式 LLM 输出，逐 token 返回，每个 token 间隔 60ms。 */
    @Override
    public Iterator<Object> stream(Object inputs, AgentSession session, List<StreamMode> streamModes) {
        String query = extractQuery(inputs);
        String[] tokens = {"【Flux 流式】", "收到：「" + query + "」", " — ", "token-1 ", "token-2 ", "token-3 "};
        return new Iterator<>() {
            private int i = 0;

            @Override
            public boolean hasNext() {
                return i < tokens.length + 1; // +1 for the final thread-name token
            }

            @Override
            public Object next() {
                sleep(50);
                // 最后一个 token 带线程名，证明迭代跑在 boundedElastic 上
                if (i == tokens.length) {
                    i++;
                    return "✓ [线程: " + Thread.currentThread().getName() + "]";
                }
                return tokens[i++];
            }
        };
    }

    private static String extractQuery(Object inputs) {
        if (inputs instanceof Map<?, ?> m) {
            Object q = m.get("query");
            return q != null ? String.valueOf(q) : String.valueOf(inputs);
        }
        return String.valueOf(inputs);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
