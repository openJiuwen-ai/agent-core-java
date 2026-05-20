package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.tools.web.WebHttpResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessCodeWebMcpToolsCompatibilityTest {

    @Test
    void codeToolShouldRunPythonAndSurfaceFailure() {
        CodeTool tool = new CodeTool();

        ToolOutput ok = tool.invoke("print('hi')", "python");
        ToolOutput bad = tool.invoke("def f(:\n  pass", "python");
        ToolOutput unsupported = tool.invoke("print(1)", "ruby");

        assertThat(ok.isSuccess()).isTrue();
        assertThat(String.valueOf(((Map<?, ?>) ok.getData()).get("stdout"))).contains("hi");
        assertThat(bad.isSuccess()).isFalse();
        assertThat(((Map<?, ?>) bad.getData()).get("exit_code")).isNotEqualTo(0);
        assertThat(unsupported.isSuccess()).isFalse();
    }

    @Test
    void webToolsShouldBuildFactoryOrderAndParseResults() {
        WebFreeSearchTool free = new WebFreeSearchTool(
                (method, url) -> {
                    if (url.contains("duckduckgo")) {
                        return new WebHttpResponse(200,
                                "<a class=\"result__a\" href=\"https://example.com/page1\">Example Title 1</a>"
                                        + "<a class=\"result__snippet\" href=\"#\">Example snippet 1</a>");
                    }
                    return new WebHttpResponse(200, "");
                },
                Map.of("FREE_SEARCH_DDG_ENABLED", "true", "FREE_SEARCH_BING_ENABLED", "false")
        );
        WebPaidSearchTool paid = new WebPaidSearchTool(
                (method, url) -> new WebHttpResponse(200, "Bocha summary answer."),
                Map.of("BOCHA_API_KEY", "test-key")
        );
        WebFetchWebpageTool fetch = new WebFetchWebpageTool((method, url) -> new WebHttpResponse(200, "<html>Hello</html>"));

        String freeResult = free.invoke("test query", 5);
        String paidResult = paid.invoke("test query", "bocha");
        ToolOutput fetched = fetch.invoke("https://example.com");
        List<Object> created = WebToolFactory.createWebTools(Map.of(
                "BOCHA_API_KEY", "k",
                "FREE_SEARCH_DDG_ENABLED", "false",
                "FREE_SEARCH_BING_ENABLED", "true"
        ));

        assertThat(freeResult).contains("Free search results (DuckDuckGo)").contains("Example Title 1");
        assertThat(paidResult).contains("Paid search results (bocha)");
        assertThat(fetched.isSuccess()).isTrue();
        assertThat(fetched.getData()).isEqualTo("<html>Hello</html>");
        assertThat(created).hasSize(3);
        assertThat(created.get(0)).isInstanceOf(WebPaidSearchTool.class);
        assertThat(created.get(1)).isInstanceOf(WebFreeSearchTool.class);
        assertThat(created.get(2)).isInstanceOf(WebFetchWebpageTool.class);
    }

    @Test
    void mcpToolsShouldMapDescriptorsAndContent() {
        McpResourceService service = new McpResourceService() {
            @Override
            public List<?> listResources(String serverId) {
                return List.of(new ResourcePojo("res://a", "Alpha", "text/plain", "first"));
            }

            @Override
            public List<?> readResource(String serverId, String uri) {
                return List.of(new ContentPojo("res://a", "text/plain", "hello"));
            }
        };

        ListMcpResourcesTool listTool = new ListMcpResourcesTool(service);
        ReadMcpResourceTool readTool = new ReadMcpResourceTool(service);

        ToolOutput listed = listTool.invoke("server-1");
        ToolOutput read = readTool.invoke("server-1", "res://a");

        assertThat(listed.isSuccess()).isTrue();
        assertThat(listed.getData()).isEqualTo(List.of(new McpResourceDescriptor("res://a", "Alpha", "text/plain", "first")));
        assertThat(read.isSuccess()).isTrue();
        assertThat(read.getData()).isEqualTo(List.of(new McpResourceContent("res://a", "text/plain", "hello")));
    }

    private record ResourcePojo(String uri, String name, String mimeType, String description) {
        public String getUri() {
            return uri;
        }

        public String getName() {
            return name;
        }

        public String getMimeType() {
            return mimeType;
        }

        public String getDescription() {
            return description;
        }
    }

    private record ContentPojo(String uri, String mimeType, String text) {
        public String getUri() {
            return uri;
        }

        public String getMimeType() {
            return mimeType;
        }

        public String getText() {
            return text;
        }
    }
}
