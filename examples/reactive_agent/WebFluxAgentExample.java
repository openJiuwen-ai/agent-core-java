package examples.reactive_agent;

import com.openjiuwen.core.runner.Runner;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * PR #630 响应式接口端到端示例 — 场景 5（Spring WebFlux SSE）
 *
 * <p>本示例演示应用侧如何直接基于框架的响应式 API 自行接入 Spring WebFlux。
 * HTTP 端点见同包下的 {@link WebFluxControllerExample}——框架不提供 starter 或 controller。
 *
 * <p>使用方式：将本类、{@link WebFluxControllerExample} 和 {@link SimpleReactiveAgentExample} 复制到你的
 * Spring Boot WebFlux 应用工程中，并由该应用工程提供 spring-boot-starter-webflux 依赖。
 *
 * <p>启动后可用以下请求验证：
 * <pre>
 *   curl -s -X POST http://localhost:8080/api/agent/invoke \
 *     -H 'Content-Type: application/json' \
 *     -d '{"agent_id":"demo-agent","inputs":{"query":"你好"}}'
 *
 *   curl -s -N -X POST http://localhost:8080/api/agent/stream \
 *     -H 'Content-Type: application/json' \
 *     -d '{"agent_id":"demo-agent","inputs":{"query":"你好"}}'
 * </pre>
 */
@SpringBootApplication
public class WebFluxAgentExample {

    private static final String GREEN = "\u001B[32m";
    private static final String BOLD = "\u001B[1m";
    private static final String RESET = "\u001B[0m";

    public static void main(String[] args) {
        SpringApplication.run(WebFluxAgentExample.class, args);
    }

    /** 启动 Runner 并将示例 Agent 注册到全局资源管理器。 */
    @Bean
    CommandLineRunner registerExampleAgent() {
        return args -> {
            Runner.start();
            SimpleReactiveAgentExample agent = new SimpleReactiveAgentExample();
            Runner.resourceMgr().addAgent(agent.getCard(), () -> agent, null);

            String sep = "═".repeat(64);
            System.out.println("\n" + BOLD + GREEN + sep);
            System.out.println("  场景 5｜Spring WebFlux SSE — 服务已就绪");
            System.out.println(sep + RESET + "\n");

            System.out.println("  # 单次调用（Mono → JSON）：");
            System.out.println("  curl -s -X POST http://localhost:8080/api/agent/invoke \\");
            System.out.println("    -H 'Content-Type: application/json' \\");
            System.out.println("    -d '{\"agent_id\":\"demo-agent\",\"inputs\":{\"query\":\"你好\"}}'");

            System.out.println();
            System.out.println("  # SSE 流式输出（Flux → text/event-stream）：");
            System.out.println("  curl -s -N -X POST http://localhost:8080/api/agent/stream \\");
            System.out.println("    -H 'Content-Type: application/json' \\");
            System.out.println("    -d '{\"agent_id\":\"demo-agent\",\"inputs\":{\"query\":\"你好\"}}'");
            System.out.println();
        };
    }
}
