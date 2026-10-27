# AI Agent Framework Performance Optimization

This file supplements SKILL.md, targeting **AI agent framework** specific performance scenarios. agent-core-java uses OkHttp + Jackson + reflection-based dynamic invocation; performance bottlenecks differ from general Java applications. Read on demand when users are optimizing agent framework performance, tuning LLM calls, optimizing JSON serialization, or tuning prompt concatenation.

## Agent Framework Performance Bottleneck Distribution

Typical AI agent request chain:

```
User input -> prompt concatenation -> LLM HTTP call (slowest) -> JSON parsing -> Tool invocation (reflection) -> Response concatenation
```

| Stage | Typical Time | Optimization Direction |
|---|---|---|
| Prompt concatenation | Microsecond-level | StringBuilder / template |
| LLM HTTP call | 500ms-30s (slowest) | Connection reuse / timeout / streaming response |
| JSON parsing | Millisecond-level | Jackson streaming / Schema caching |
| Reflection invocation | Microsecond-level | MethodHandle / LambdaMetafactory |
| Response concatenation | Microsecond-level | StringBuilder / streaming |

**Core conclusion**: LLM HTTP calls account for 90%+ of time. Code-level optimization has limited benefit, but don't let the code layer become a drag.

## LLM HTTP Call Optimization

### OkHttp Connection Pool Configuration

OkHttp default connection pool: 5 idle connections, 5-minute timeout. Insufficient for agent high-frequency LLM API calls.

**Default configuration issues**:
- Default 5 connections -> frequent new TCP connections under high concurrency
- Default 5-minute keep-alive -> closed after long idle period, rebuilt next time

**Optimized configuration**:
```java
ConnectionPool pool = new ConnectionPool(
    20,                       // Max idle connections
    5,                        // keep-alive minutes
    TimeUnit.MINUTES
);

OkHttpClient client = new OkHttpClient.Builder()
    .connectionPool(pool)
    .connectTimeout(10, TimeUnit.SECONDS)      // Connection timeout
    .readTimeout(120, TimeUnit.SECONDS)        // LLM response is slow, give enough time
    .writeTimeout(30, TimeUnit.SECONDS)
    .callTimeout(180, TimeUnit.SECONDS)        // Overall timeout (including retries)
    .retryOnConnectionFailure(true)            // Retry on connection failure
    .protocols(Arrays.asList(Protocol.HTTP_1_1))  // LLM APIs mostly use HTTP/1.1
    .build();
```

**Key parameters**:
- `readTimeout`: LLM long output (thousands of tokens) may take 30s+, set to 120s
- `callTimeout`: Overall including retries, set to 180s
- `protocols`: HTTP/2 multiplexing is better, but some LLM APIs don't support it; HTTP/1.1 is compatible
- `connectionPool`: Adjust based on concurrency level; concurrency 20 -> 20 connections

### Connection Reuse Benefit

| Scenario | New Connection | Reused Connection | Benefit |
|---|---|---|---|
| TCP handshake + TLS | 100-300ms | 0 | Save hundreds of milliseconds |
| DNS resolution | 10-50ms | 0 | Save DNS |
| First byte latency | Slightly higher | Slightly lower | 5-20% |

**Reuse condition**: Same host + same port + same HTTP version.

### Streaming Response Processing

LLM streaming response (SSE / chunked) is 10x+ faster in perceived experience than one-shot response:

```java
// Streaming: first token in milliseconds, subsequent tokens returned incrementally
Request request = new Request.Builder()
    .url("https://api.openai.com/v1/chat/completions")
    .post(RequestBody.create(payload, MediaType.parse("application/json")))
    .build();

try (Response response = client.newCall(request).execute()) {
    try (BufferedSource source = response.body().source()) {
        while (!source.exhausted()) {
            String line = source.readUtf8Line();
            if (line != null && line.startsWith("data: ")) {
                // Process SSE event
                handleSseEvent(line.substring(6));
            }
        }
    }
}
```

**Notes**:
- Streaming `readTimeout` is per-read timeout, not overall timeout
- Use `callTimeout` to control overall timeout
- Don't use `response.body().string()` -- blocks until everything is read

### Timeout Strategy

| Timeout Type | Recommended Value | Reason |
|---|---|---|
| `connectTimeout` | 10s | Slow connection is usually a network issue |
| `readTimeout` | 120s | LLM long output is slow |
| `writeTimeout` | 30s | Request body is generally small |
| `callTimeout` | 180s | Overall including retries |
| `retryOnConnectionFailure` | true | Reconnect on network jitter |

### Retry and Backoff

LLM API rate limiting (429) is common. Exponential backoff:

```java
public Response callWithRetry(Request request, int maxRetries) throws IOException {
    int attempt = 0;
    while (true) {
        try {
            return client.newCall(request).execute();
        } catch (IOException e) {
            if (++attempt > maxRetries) throw e;
            long backoff = (long) Math.pow(2, attempt) * 1000;  // 1s, 2s, 4s, ...
            sleep(backoff);
        }
    }
}
```

**Notes**:
- On 429, check `Retry-After` header
- Don't use OkHttp default retry (only default retry (only retries connection, not the call)

## JSON Serialization Optimization

Agent frameworks use JSON heavily (LLM responses, tool parameters, configuration). Jackson is mainstream, but using it incorrectly is slow.

### Jackson Performance Key Points

**1. Reuse ObjectMapper**

```java
// Wrong: new instance each time
ObjectMapper mapper = new ObjectMapper();  // Heavyweight, slow initialization
String json = mapper.writeValueAsString(obj);

// Right: singleton reuse
private static final ObjectMapper MAPPER = new ObjectMapper();
// Or inject via Spring
```

**2. Use streaming API for large payloads**

```java
// Slow: DOM tree (read everything into memory then process)
JsonNode root = MAPPER.readTree(json);
String text = root.get("choices").get(0).get("message").get("content").asText();

// Fast: streaming (parse while reading)
try (JsonParser parser = MAPPER.getFactory().createParser(json)) {
    while (parser.nextToken() != null) {
        if ("content".equals(parser.getCurrentName())) {
            parser.nextToken();
            String content = parser.getText();
            break;
        }
    }
}
```

**3. Disable unused features**:
```java
ObjectMapper mapper = new ObjectMapper();
mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
mapper.disable(SerializationFeature.INDENT_OUTPUT);  // Disable indentation in production
```

**4. Schema caching**:

```java
// Slow: reflection serialization each time
mapper.writeValueAsString(obj);  // Internal reflection + type discovery

// Fast: pre-generated schema
SimpleModule module = new SimpleModule();
module.addSerializer(MyPojo.class, new MyPojoSerializer());  // Hand-written or pre-generated
mapper.registerModule(module);
```

### Jackson vs Gson vs JsonB

| Library | Performance | Size | Notes |
|---|---|---|---|
| **Jackson** | Fast | Medium | Mainstream, full ecosystem |
| **Gson** | Slow (about 30-50%) | Small | Simple and easy to use |
| **JsonB** (Yasson) | Close to Jackson | Medium | Standard API |
| **jsoniter** | Fastest | Small | Third-party |
| **protobuf** | 5-10x faster than JSON | Binary | Cross-language, requires schema |

**Selection**: Agent frameworks use Jackson as mainstream; for extreme performance use jsoniter or protobuf (requires LLM API support).

### LLM Response JSON Parsing

LLM-returned JSON is often non-standard (extra fields / field type changes / contains markdown code blocks):

```java
// Defensive parsing
mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);

// Strip ```json code block
String cleaned = rawJson.replaceAll("^```json\\s*", "").replaceAll("\\s*```$", "");
```

## Reflection and Dynamic Invocation Optimization

Agent frameworks have many dynamic invocations of tools/agents, creating many reflection hotspots.

### Tool Invocation Reflection Optimization

Agent tools typically: method name + parameter map -> reflectively call Java method.

**Anti-pattern**: Reflectively look up method each time:
```java
public Object invokeTool(Object target, String method, Object[] args) throws Exception {
    Method m = target.getClass().getMethod(method, ...);  // Reflective lookup each time
    return m.invoke(target, args);
}
```

**Optimization 1: Cache Method**:
```java
private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

public Object invokeTool(Object target, String method, Object[] args) throws Exception {
    String key = target.getClass().getName() + "#" + method;
    Method m = METHOD_CACHE.computeIfAbsent(key, k -> {
        try {
            Method mm = target.getClass().getMethod(method);
            mm.setAccessible(true);
            return mm;
        } catch (NoSuchMethodException e) { throw new RuntimeException(e); }
    });
    return m.invoke(target, args);
}
```

**Optimization 2: MethodHandle** (faster):
```java
private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
private static final Map<String, MethodHandle> MH_CACHE = new ConcurrentHashMap<>();

public Object invokeTool(Object target, String method) throws Throwable {
    String key = target.getClass().getName() + "#" + method;
    MethodHandle mh = MH_CACHE.computeIfAbsent(key, k -> {
        try {
            return LOOKUP.findVirtual(target.getClass(), method,
                MethodType.methodType(Object.class));
        } catch (Exception e) { throw new RuntimeException(e); }
    });
    return mh.invoke(target);
}
```

**Optimization 3: LambdaMetafactory** (fastest, close to direct call):
```java
// Convert MethodHandle to Function, no reflection overhead at runtime
Function<Object, Object> fn = (Function<Object, Object>) LambdaMetafactory.metafactory(
    LOOKUP, "apply", MethodType.methodType(Function.class),
    MethodType.methodType(Object.class, Object.class),
    mh, MethodType.methodType(Object.class, target.getClass())
).getTarget().invoke();
```

### JDK 17 Module System Notes

Reflective access to non-exported packages requires `--add-opens`:

```
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED
```

See jvm-troubleshoot SKILL.md [Module System Troubleshooting](../../jvm-troubleshoot/SKILL.md#module-system-troubleshooting).

## Prompt Concatenation Performance

Prompt templates with hundreds of lines of concatenation; simple StringBuilder is sufficient, don't use complex template engines.

### Template Engine Performance Comparison

| Approach | Performance | Flexibility | Notes |
|---|---|---|---|
| `+` string concatenation | Slow (in loops) | Low | Compiler auto-optimizes in simple cases |
| `StringBuilder` | Fast | Medium | Mainstream, sufficient |
| `String.format` | Slow | Medium | Don't use on hot paths |
| `MessageFormat` | Slow | Medium | Don't use |
| `StringSubstitutor` (Apache) | Medium | High | Convenient variable substitution |
| Mustache / Handlebars | Slow | High | Use for complex templates |
| JTE (compiled templates) | Fastest | High | Generates bytecode at compile time |

**Recommendations**:
- Simple templates -> `StringBuilder` + variable substitution
- Complex templates -> JTE (compile-time optimization, performance close to StringBuilder)

### StringBuilder Optimization

```java
// Wrong: + inside loop
String prompt = "";
for (String line : lines) {
    prompt += line + "\n";  // New StringBuilder each iteration
}

// Right: StringBuilder outside loop
StringBuilder sb = new StringBuilder(1024);  // Estimate capacity to avoid resizing
for (String line : lines) {
    sb.append(line).append('\n');
}
String prompt = sb.toString();
```

**Estimate capacity**: Prompt length can be estimated; `new StringBuilder(expectedSize)` avoids resizing copies.

## Async Orchestration Optimization

Agent multi-step reasoning (e.g., ReAct) uses `CompletableFuture` orchestration:

```java
// Sequential: slow
CompletableFuture<String> f1 = callLlm(prompt1);
String r1 = f1.join();
CompletableFuture<String> f2 = callLlm(r1 + prompt2);
String r2 = f2.join();

// Parallel independent steps: fast
CompletableFuture<String> f1 = callLlm(prompt1);
CompletableFuture<String> f2 = callLlm(prompt2);  // Independent, can be parallelized
CompletableFuture<Void> all = CompletableFuture.allOf(f1, f2);
all.join();
```

**Notes**:
- LLM calls are IO; for parallel execution use a dedicated thread pool (don't use ForkJoinPool.commonPool)
- Don't do CPU-intensive logic inside `CompletableFuture` (blocks the thread pool)
- Streaming response + CompletableFuture combination is complex; Reactor is more suitable

## Caching Strategy

Cacheable items in agent frameworks:

| Cache Target | Hit Rate | Benefit | Invalidation Strategy |
|---|---|---|---|
| LLM response (same prompt) | Medium | Huge (saves LLM call) | Prompt hash + TTL |
| ObjectMapper | 100% (singleton) | Medium | Never invalidates |
| Method / MethodHandle | 100% (cached) | Small | Never invalidates |
| Embedding vector | High | Large | Content hash + TTL |
| Tool description | High | Medium | Tool list change |

**LLM response caching**:
```java
Cache<String, String> llmCache = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterAccess(1, TimeUnit.HOURS)
    .build();

String response = llmCache.get(promptHash, k -> callLlm(prompt));
```

**Notes**:
- LLM responses have randomness (temperature > 0); caching is only effective for temperature=0
- Minor prompt changes -> cache miss; prompts need to be normalized

## Performance Testing (Agent Framework Specific)

### LLM Call Benchmark

```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class LlmCallBenchmark {

    OkHttpClient client;
    Request request;

    @Setup
    public void setup() {
        client = new OkHttpClient.Builder()
            .connectionPool(new ConnectionPool(20, 5, TimeUnit.MINUTES))
            .build();
        request = new Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(RequestBody.create("{\"model\":\"gpt-4\",\"messages\":[...]}", MediaType.parse("application/json")))
            .build();
    }

    @Benchmark
    public String callLlm() throws IOException {
        try (Response r = client.newCall(request).execute()) {
            return r.body().string();
        }
    }
}
```

**Note**: LLM calls are IO; JMH single-threaded benchmarks may not represent real load. For production load testing use wrk / k6.

### JSON Parsing Benchmark

```java
@Benchmark
public String parseLlmResponse(Blackhole bh) throws IOException {
    JsonNode root = MAPPER.readTree(LLM_RESPONSE_JSON);
    bh.consume(root.get("choices").get(0).get("message").get("content").asText());
    return root.toString();
}
```

## Practical Optimization Checklist

### LLM Call Layer
- [ ] Tune OkHttp connection pool to match concurrency level
- [ ] Set sufficient `readTimeout` (120s+)
- [ ] Process streaming responses (fast first token)
- [ ] Exponential backoff retry on 429 rate limiting
- [ ] Cache LLM responses when temperature=0

### JSON Serialization Layer
- [ ] ObjectMapper singleton reuse
- [ ] Use streaming API for large payloads
- [ ] Disable INDENT_OUTPUT (in production)
- [ ] Disable FAIL_ON_UNKNOWN_PROPERTIES

### Reflection / Dynamic Invocation Layer
- [ ] Cache tool Method objects
- [ ] Switch to MethodHandle (5-10x performance)
- [ ] Use LambdaMetafactory on critical paths
- [ ] Add `--add-opens` for JDK 17

### Prompt / Response Concatenation Layer
- [ ] StringBuilder with estimated capacity
- [ ] Don't use `+` concatenation in loops
- [ ] Use JTE for complex templates (compile-time optimization)

### Async Orchestration Layer
- [ ] Parallelize independent steps
- [ ] Use dedicated thread pool for LLM calls (don't use commonPool)
- [ ] Use Reactor for streaming + complex orchestration

## Reference Documentation

- In-project general performance: `../SKILL.md`
- In-project code-level optimization: `code_level_optimization.md` (collections / reflection / lock details)
- In-project JMH details: `jmh_profiling.md`
- OkHttp documentation: `https://square.github.io/okhttp/`
- Jackson performance: `https://github.com/FasterXML/jackson-docs`
- JTE template: `https://github.com/jknack/jte`
- LLM streaming response: `https://platform.openai.com/docs/api-reference/chat-streaming`
