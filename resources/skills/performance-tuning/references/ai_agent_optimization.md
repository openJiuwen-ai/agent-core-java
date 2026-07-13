# AI Agent 框架性能优化

本文件补充 SKILL.md，针对 **AI agent 框架**特定性能场景。agent-core-java 用 OkHttp + Jackson + 反射动态调用，性能瓶颈与通用 Java 应用不同。用户做 agent 框架性能优化、调 LLM 调用、优化 JSON 序列化、调 prompt 拼接时按需读取。

## agent 框架性能瓶颈分布

典型 AI agent 请求链路：

```
用户输入 → prompt 拼接 → LLM HTTP 调用（最慢）→ JSON 解析 → 工具调用（反射）→ 响应拼接
```

| 阶段 | 典型耗时 | 优化方向 |
|---|---|---|
| prompt 拼接 | 微秒级 | StringBuilder / 模板 |
| LLM HTTP 调用 | 500ms-30s（最慢） | 连接复用 / 超时 / 流式响应 |
| JSON 解析 | 毫秒级 | Jackson 流式 / Schema 缓存 |
| 反射调用 | 微秒级 | MethodHandle / LambdaMetafactory |
| 响应拼接 | 微秒级 | StringBuilder / streaming |

**核心结论**：LLM HTTP 调用占 90%+ 耗时。代码层优化收益有限，但别让代码层拖后腿。

## LLM HTTP 调用优化

### OkHttp 连接池配置

OkHttp 默认连接池：5 个空闲连接，5 分钟超时。agent 高频调 LLM API 不够。

**默认配置问题**：
- 默认 5 连接 → 高并发时频繁新建 TCP 连接
- 默认 5 分钟 keep-alive → 长时间空闲后被关，下次重建

**优化配置**：
```java
ConnectionPool pool = new ConnectionPool(
    20,                       // 最大空闲连接数
    5,                        // keep-alive 分钟数
    TimeUnit.MINUTES
);

OkHttpClient client = new OkHttpClient.Builder()
    .connectionPool(pool)
    .connectTimeout(10, TimeUnit.SECONDS)      // 建连超时
    .readTimeout(120, TimeUnit.SECONDS)        // LLM 响应慢，给足
    .writeTimeout(30, TimeUnit.SECONDS)
    .callTimeout(180, TimeUnit.SECONDS)        // 整体超时（含重试）
    .retryOnConnectionFailure(true)            // 连接失败重试
    .protocols(Arrays.asList(Protocol.HTTP_1_1))  // LLM API 多用 HTTP/1.1
    .build();
```

**关键参数**：
- `readTimeout`：LLM 长输出（千 tokens）可能 30s+，给 120s
- `callTimeout`：整体含重试，给 180s
- `protocols`：HTTP/2 多路复用更好，但部分 LLM API 不支持，HTTP/1.1 兼容
- `connectionPool`：按并发度调，并发 20 → 20 连接

### 连接复用收益

| 场景 | 新建连接 | 复用连接 | 收益 |
|---|---|---|---|
| TCP 握手 + TLS | 100-300ms | 0 | 省数百毫秒 |
| DNS 解析 | 10-50ms | 0 | 省 DNS |
| 首字节延迟 | 略高 | 略低 | 5-20% |

**复用条件**：同一 host + 同一端口 + 同一 HTTP 版本。

### 流式响应处理

LLM 流式响应（SSE / chunked）比一次性响应快 10x+ 体验：

```java
// 流式：首 token 毫秒级，后续增量返回
Request request = new Request.Builder()
    .url("https://api.openai.com/v1/chat/completions")
    .post(RequestBody.create(payload, MediaType.parse("application/json")))
    .build();

try (Response response = client.newCall(request).execute()) {
    try (BufferedSource source = response.body().source()) {
        while (!source.exhausted()) {
            String line = source.readUtf8Line();
            if (line != null && line.startsWith("data: ")) {
                // 处理 SSE 事件
                handleSseEvent(line.substring(6));
            }
        }
    }
}
```

**注意**：
- 流式 `readTimeout` 是单次读超时，不是整体超时
- 用 `callTimeout` 控制整体
- 别用 `response.body().string()` —— 阻塞到全部读完

### 超时策略

| 超时类型 | 推荐值 | 理由 |
|---|---|---|
| `connectTimeout` | 10s | 建连慢基本网络问题 |
| `readTimeout` | 120s | LLM 长输出慢 |
| `writeTimeout` | 30s | 请求体一般小 |
| `callTimeout` | 180s | 含重试整体 |
| `retryOnConnectionFailure` | true | 网络抖动重连 |

### 重试与退避

LLM API 限流（429）常见。指数退避：

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

**注意**：
- 429 时看 `Retry-After` header
- 别用 OkHttp 默认重试（只重连不重调）

## JSON 序列化优化

agent 框架 JSON 用得多（LLM 响应、工具参数、配置）。Jackson 主流，但用错慢。

### Jackson 性能要点

**1. 复用 ObjectMapper**

```java
// 错：每次 new
ObjectMapper mapper = new ObjectMapper();  // 重量级，初始化慢
String json = mapper.writeValueAsString(obj);

// 对：单例复用
private static final ObjectMapper MAPPER = new ObjectMapper();
// 或 Spring 注入
```

**2. 用流式 API 处理大 payload**

```java
// 慢：DOM 树（读全到内存再处理）
JsonNode root = MAPPER.readTree(json);
String text = root.get("choices").get(0).get("message").get("content").asText();

// 快：流式（边读边解析）
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

**3. 关闭未用特性**：
```java
ObjectMapper mapper = new ObjectMapper();
mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
mapper.disable(SerializationFeature.INDENT_OUTPUT);  // 生产关缩进
```

**4. Schema 缓存**：

```java
// 慢：每次反射序列化
mapper.writeValueAsString(obj);  // 内部反射 + 类型发现

// 快：预生成 schema
SimpleModule module = new SimpleModule();
module.addSerializer(MyPojo.class, new MyPojoSerializer());  // 手写或预生成
mapper.registerModule(module);
```

### Jackson vs Gson vs JsonB

| 库 | 性能 | 体积 | 备注 |
|---|---|---|---|
| **Jackson** | 快 | 中 | 主流，生态全 |
| **Gson** | 慢（约 30-50%） | 小 | 简单易用 |
| **JsonB**（Yasson） | 接近 Jackson | 中 | 标准 API |
| **jsoniter** | 最快 | 小 | 第三方 |
| **protobuf** | 比 JSON 快 5-10x | 二进制 | 跨语言，需 schema |

**选**：agent 框架用 Jackson 主流；性能极致用 jsoniter 或 protobuf（需 LLM API 支持）。

### LLM 响应 JSON 解析

LLM 返回 JSON 经常不规范（多字段 / 字段类型变 / 含 markdown 代码块）：

```java
// 防御性解析
mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);

// 剥离 ```json 代码块
String cleaned = rawJson.replaceAll("^```json\\s*", "").replaceAll("\\s*```$", "");
```

## 反射与动态调用优化

agent 框架动态调用工具 / agent 多，反射热点多。

### 工具调用反射优化

agent 工具通常：方法名 + 参数 map → 反射调 Java 方法。

**反模式**：每次反射查方法：
```java
public Object invokeTool(Object target, String method, Object[] args) throws Exception {
    Method m = target.getClass().getMethod(method, ...);  // 每次反射查
    return m.invoke(target, args);
}
```

**优化 1：缓存 Method**：
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

**优化 2：MethodHandle**（更快）：
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

**优化 3：LambdaMetafactory**（最快，接近直接调用）：
```java
// 把 MethodHandle 转成 Function，运行时无反射开销
Function<Object, Object> fn = (Function<Object, Object>) LambdaMetafactory.metafactory(
    LOOKUP, "apply", MethodType.methodType(Function.class),
    MethodType.methodType(Object.class, Object.class),
    mh, MethodType.methodType(Object.class, target.getClass())
).getTarget().invoke();
```

### JDK 17 模块系统注意

反射访问非 export 包需 `--add-opens`：

```
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED
```

详见 jvm-troubleshoot SKILL.md [模块系统排查](../../jvm-troubleshoot/SKILL.md#模块系统排查)。

## prompt 拼接性能

prompt 几百行模板拼接，简单 StringBuilder 够用，别用复杂模板引擎。

### 模板引擎性能对比

| 方案 | 性能 | 灵活性 | 备注 |
|---|---|---|---|
| `+` 字符串拼接 | 慢（循环内） | 低 | 简单场景编译器自动优化 |
| `StringBuilder` | 快 | 中 | 主流，足够 |
| `String.format` | 慢 | 中 | 别用热路径 |
| `MessageFormat` | 慢 | 中 | 别用 |
| `StringSubstitutor`（Apache） | 中 | 高 | 变量替换方便 |
| Mustache / Handlebars | 慢 | 高 | 模板复杂时用 |
| JTE（compiled templates） | 最快 | 高 | 编译时生成字节码 |

**推荐**：
- 简单模板 → `StringBuilder` + 变量替换
- 复杂模板 → JTE（编译时优化，性能接近 StringBuilder）

### StringBuilder 优化

```java
// 错：循环内 +
String prompt = "";
for (String line : lines) {
    prompt += line + "\n";  // 每次新建 StringBuilder
}

// 对：循环外 StringBuilder
StringBuilder sb = new StringBuilder(1024);  // 预估容量避免扩容
for (String line : lines) {
    sb.append(line).append('\n');
}
String prompt = sb.toString();
```

**预估容量**：prompt 长度可预估，`new StringBuilder(expectedSize)` 避免扩容拷贝。

## 异步编排优化

agent 多步推理（如 ReAct）用 `CompletableFuture` 编排：

```java
// 串行：慢
CompletableFuture<String> f1 = callLlm(prompt1);
String r1 = f1.join();
CompletableFuture<String> f2 = callLlm(r1 + prompt2);
String r2 = f2.join();

// 并行独立步骤：快
CompletableFuture<String> f1 = callLlm(prompt1);
CompletableFuture<String> f2 = callLlm(prompt2);  // 独立可并行
CompletableFuture<Void> all = CompletableFuture.allOf(f1, f2);
all.join();
```

**注意**：
- LLM 调用是 IO，并行多个用独立线程池（别用 ForkJoinPool.commonPool）
- `CompletableFuture` 内别做 CPU 密集逻辑（阻塞线程池）
- 流式响应 + CompletableFuture 组合复杂，用 Reactor 更适合

## 缓存策略

agent 框架可缓存的：

| 缓存对象 | 命中率 | 收益 | 失效策略 |
|---|---|---|---|
| LLM 响应（同 prompt） | 中 | 巨大（省 LLM 调用） | prompt hash + TTL |
| ObjectMapper | 100%（单例） | 中 | 不失效 |
| Method / MethodHandle | 100%（缓存） | 小 | 不失效 |
| Embedding 向量 | 高 | 大 | 内容 hash + TTL |
| 工具描述 | 高 | 中 | 工具列表变更 |

**LLM 响应缓存**：
```java
Cache<String, String> llmCache = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterAccess(1, TimeUnit.HOURS)
    .build();

String response = llmCache.get(promptHash, k -> callLlm(prompt));
```

**注意**：
- LLM 响应有随机性（temperature > 0），缓存只对 temperature=0 有效
- prompt 微小变化 → 缓存 miss，需规范化 prompt

## 性能测试（agent 框架特定）

### LLM 调用基准

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

**注意**：LLM 调用是 IO，JMH 单线程基准不一定代真实负载。生产压测用 wrk / k6。

### JSON 解析基准

```java
@Benchmark
public String parseLlmResponse(Blackhole bh) throws IOException {
    JsonNode root = MAPPER.readTree(LLM_RESPONSE_JSON);
    bh.consume(root.get("choices").get(0).get("message").get("content").asText());
    return root.toString();
}
```

## 实战优化清单

### LLM 调用层
- [ ] OkHttp 连接池调到并发数
- [ ] `readTimeout` 给足（120s+）
- [ ] 流式响应处理（首 token 快）
- [ ] 429 限流指数退避重试
- [ ] temperature=0 时缓存 LLM 响应

### JSON 序列化层
- [ ] ObjectMapper 单例复用
- [ ] 大 payload 用流式 API
- [ ] 关闭 INDENT_OUTPUT（生产）
- [ ] 关闭 FAIL_ON_UNKNOWN_PROPERTIES

### 反射 / 动态调用层
- [ ] 工具 Method 缓存
- [ ] 改 MethodHandle（性能 5-10x）
- [ ] 关键路径用 LambdaMetafactory
- [ ] JDK 17 加 `--add-opens`

### prompt / 响应拼接层
- [ ] StringBuilder 预估容量
- [ ] 别循环内 `+` 拼接
- [ ] 复杂模板用 JTE（编译时优化）

### 异步编排层
- [ ] 独立步骤并行化
- [ ] LLM 调用用独立线程池（别用 commonPool）
- [ ] 流式 + 复杂编排用 Reactor

## 参考文档

- 项目内通用性能：`../SKILL.md`
- 项目内代码级优化：`code_level_optimization.md`（集合 / 反射 / 锁细节）
- 项目内 JMH 详解：`jmh_profiling.md`
- OkHttp 文档：`https://square.github.io/okhttp/`
- Jackson 性能：`https://github.com/FasterXML/jackson-docs`
- JTE 模板：`https://github.com/jknack/jte`
- LLM 流式响应：`https://platform.openai.com/docs/api-reference/chat-streaming`
