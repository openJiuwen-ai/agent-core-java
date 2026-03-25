# Foundation 模块 API 文档

> 包路径：`com.openjiuwen.core.foundation`

LLM 模型、Prompt、Tool 与基础存储适配能力。基于 `foundation` 包源码逐页复核整理。

## 文档说明

- 本页覆盖 `87` 个公开类型（含嵌套公开类型）。
- 默认记录源码中显式声明的 public/protected API；接口中按语言规则公开的成员同样列出。
- Lombok 自动生成的 getter/setter/builder 不逐项展开，DTO/配置类改为记录显式字段。
- 标记为 `@Deprecated` 或位于 `legacy` 包的类型会在条目中注明兼容性。

## 包概览

| 包 | 公开类型数 |
|---|---:|
| `com.openjiuwen.core.foundation.llm` | 3 |
| `com.openjiuwen.core.foundation.llm.model_clients` | 9 |
| `com.openjiuwen.core.foundation.llm.output_parsers` | 6 |
| `com.openjiuwen.core.foundation.llm.schema` | 21 |
| `com.openjiuwen.core.foundation.prompt` | 1 |
| `com.openjiuwen.core.foundation.prompt.assemble` | 1 |
| `com.openjiuwen.core.foundation.prompt.assemble.variables` | 3 |
| `com.openjiuwen.core.foundation.store` | 1 |
| `com.openjiuwen.core.foundation.store.db` | 1 |
| `com.openjiuwen.core.foundation.store.graph` | 1 |
| `com.openjiuwen.core.foundation.store.kv` | 2 |
| `com.openjiuwen.core.foundation.store.object` | 1 |
| `com.openjiuwen.core.foundation.store.vector` | 4 |
| `com.openjiuwen.core.foundation.store.vector_fields` | 4 |
| `com.openjiuwen.core.foundation.tool` | 2 |
| `com.openjiuwen.core.foundation.tool.annotation` | 1 |
| `com.openjiuwen.core.foundation.tool.function` | 2 |
| `com.openjiuwen.core.foundation.tool.mcp` | 4 |
| `com.openjiuwen.core.foundation.tool.mcp.client` | 5 |
| `com.openjiuwen.core.foundation.tool.schema` | 2 |
| `com.openjiuwen.core.foundation.tool.service_api` | 4 |
| `com.openjiuwen.core.foundation.tool.service_api.parser` | 7 |
| `com.openjiuwen.core.foundation.tool.utils` | 2 |

## `com.openjiuwen.core.foundation.llm`

公开类型：`3`

### `InferenceAffinityModel`

- 类型：`class`
- 声明：`public class InferenceAffinityModel`
- 说明：Unified entry point for InferenceAffinity (vLLM-style) invocation.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public InferenceAffinityModel(ModelClientConfig modelClientConfig, ModelRequestConfig modelConfig)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ModelRequestConfig getModelConfig()` | `ModelRequestConfig` | - |
| `public ModelClientConfig getModelClientConfig()` | `ModelClientConfig` | - |
| `public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, Integer maxTokens, String stop, String model, BaseOutputParser outputParser, String sessionId, boolean enableCacheSharing, Map<String, Object> kwargs) throws Exception` | `AssistantMessage` | - |
| `public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP, Integer maxTokens, String stop, String model, BaseOutputParser outputParser, String sessionId, boolean enableCacheSharing, Map<String, Object> kwargs) throws Exception` | `Iterator<AssistantMessageChunk>` | - |
| `public boolean release(String sessionId, List<?> messages, int messagesReleasedIndex, List<?> tools, Integer toolsReleasedIndex, String model) throws Exception` | `boolean` | - |

### `Model`

- 类型：`class`
- 声明：`public class Model`
- 说明：Unified LLM invocation entry point.
- 嵌套公开类型：`Model.ModelClientFactory`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Model(ModelClientConfig modelClientConfig, ModelRequestConfig modelConfig)` | Construct a Model. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static void registerFactory(ModelClientFactory factory)` | `void` | Register a model client factory programmatically. |
| `public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception` | `AssistantMessage` | - |
| `public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception` | `Iterator<AssistantMessageChunk>` | - |
| `public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size, String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed, Map<String, Object> kwargs) throws Exception` | `ImageGenerationResponse` | - |
| `public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice, String languageType, Map<String, Object> kwargs) throws Exception` | `AudioGenerationResponse` | - |
| `public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl, String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark, String negativePrompt, Integer seed, Map<String, Object> kwargs) throws Exception` | `VideoGenerationResponse` | - |

### `Model.ModelClientFactory`

- 类型：`interface`
- 声明：`public interface ModelClientFactory`
- 说明：SPI-based registry for model client factories.
- 宿主类型：`Model`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `String providerName()` | `String` | The provider name this factory handles (e.g., "OpenAI", "DashScope"). |
| `BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig)` | `BaseModelClient` | Create a client instance. |

## `com.openjiuwen.core.foundation.llm.model_clients`

公开类型：`9`

### `BaseModelClient`

- 类型：`class`
- 声明：`public abstract class BaseModelClient`
- 说明：LLM Model Client abstract base class.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `modelConfig` | `ModelRequestConfig` | `protected final` | `-` | - |
| `modelClientConfig` | `ModelClientConfig` | `protected final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected BaseModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig)` | Initialize the model client. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected String getClientName()` | `String` | Get client name for error messages. |
| `protected void validateConfig()` | `void` | Validate configuration parameters. |
| `protected List<Map<String, Object>> convertMessagesToDict(Object messages)` | `List<Map<String, Object>>` | Convert messages to a list of dicts in OpenAI format. |
| `protected List<Map<String, Object>> convertToolsToDict(Object tools)` | `List<Map<String, Object>>` | Convert tools to OpenAI format. |
| `protected Map<String, Object> buildRequestParams(Object messages, Object tools, Double temperature, Double topP, String model, String stop, Integer maxTokens, boolean stream, Map<String, Object> extraKwargs)` | `Map<String, Object>` | Build OpenAI-compatible request parameters. |
| `public abstract AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception` | `AssistantMessage` | Invoke the LLM (synchronous, blocking via virtual thread). |
| `public abstract Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception` | `Iterator<AssistantMessageChunk>` | Stream invoke the LLM. |
| `public abstract ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size, String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed, Map<String, Object> kwargs) throws Exception` | `ImageGenerationResponse` | Generate an image from a text prompt. |
| `public abstract AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice, String languageType, Map<String, Object> kwargs) throws Exception` | `AudioGenerationResponse` | Generate speech audio from text. |
| `public abstract VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl, String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark, String negativePrompt, Integer seed, Map<String, Object> kwargs) throws Exception` | `VideoGenerationResponse` | Generate video from a text prompt. |

### `DashScopeModelClientFactory`

- 类型：`class`
- 声明：`public class DashScopeModelClientFactory implements Model.ModelClientFactory`
- 说明：Default factory for the DashScope provider.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String providerName()` | `String` | - |
| `public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig)` | `BaseModelClient` | - |

### `DefaultModelClientFactories`

- 类型：`class`
- 声明：`public final class DefaultModelClientFactories`
- 说明：Registers the built-in OpenAI-compatible model client factories.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static synchronized void ensureRegistered()` | `void` | - |

### `InferenceAffinityModelClient`

- 类型：`class`
- 声明：`public class InferenceAffinityModelClient extends BaseModelClient`
- 说明：Inference Affinity (vLLM-style) client with cache sharing and release support.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public InferenceAffinityModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected String getClientName()` | `String` | - |
| `protected void validateConfig()` | `void` | - |
| `public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception` | `AssistantMessage` | - |
| `public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception` | `Iterator<AssistantMessageChunk>` | - |
| `public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size, String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed, Map<String, Object> kwargs)` | `ImageGenerationResponse` | - |
| `public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice, String languageType, Map<String, Object> kwargs)` | `AudioGenerationResponse` | - |
| `public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl, String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark, String negativePrompt, Integer seed, Map<String, Object> kwargs)` | `VideoGenerationResponse` | - |
| `public boolean release(String sessionId, Object messages, int messagesReleasedIndex, Object tools, Integer toolsReleasedIndex, String model) throws Exception` | `boolean` | - |

### `InferenceAffinityModelClientFactory`

- 类型：`class`
- 声明：`public class InferenceAffinityModelClientFactory implements Model.ModelClientFactory`
- 说明：Factory for InferenceAffinity model clients.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public InferenceAffinityModelClientFactory(String providerName)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String providerName()` | `String` | - |
| `public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig)` | `BaseModelClient` | - |

### `OpenAiCompatibleModelClient`

- 类型：`class`
- 声明：`public class OpenAiCompatibleModelClient extends BaseModelClient`
- 说明：Basic OpenAI-compatible HTTP client used by the built-in providers.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public OpenAiCompatibleModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected String getClientName()` | `String` | - |
| `protected void validateConfig()` | `void` | - |
| `public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception` | `AssistantMessage` | - |
| `public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception` | `Iterator<AssistantMessageChunk>` | - |
| `public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size, String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed, Map<String, Object> kwargs)` | `ImageGenerationResponse` | - |
| `public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice, String languageType, Map<String, Object> kwargs)` | `AudioGenerationResponse` | - |
| `public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl, String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark, String negativePrompt, Integer seed, Map<String, Object> kwargs)` | `VideoGenerationResponse` | - |

### `OpenAiModelClientFactory`

- 类型：`class`
- 声明：`public class OpenAiModelClientFactory implements Model.ModelClientFactory`
- 说明：Default factory for the OpenAI provider.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String providerName()` | `String` | - |
| `public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig)` | `BaseModelClient` | - |

### `OpenRouterModelClientFactory`

- 类型：`class`
- 声明：`public class OpenRouterModelClientFactory implements Model.ModelClientFactory`
- 说明：Default factory for the OpenRouter provider alias.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String providerName()` | `String` | - |
| `public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig)` | `BaseModelClient` | - |

### `SiliconFlowModelClientFactory`

- 类型：`class`
- 声明：`public class SiliconFlowModelClientFactory implements Model.ModelClientFactory`
- 说明：Default factory for the SiliconFlow provider.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String providerName()` | `String` | - |
| `public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig)` | `BaseModelClient` | - |

## `com.openjiuwen.core.foundation.llm.output_parsers`

公开类型：`6`

### `BaseOutputParser`

- 类型：`class`
- 声明：`public abstract class BaseOutputParser`
- 说明：Base class for parsing LLM output into the desired format.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public abstract Object parse(Object inputs) throws Exception` | `Object` | Parse LLM output. |
| `public abstract Iterator<Object> streamParse(Iterator<?> streamingInputs) throws Exception` | `Iterator<Object>` | Parse streaming LLM output. |

### `JsonOutputParser`

- 类型：`class`
- 声明：`public class JsonOutputParser extends BaseOutputParser`
- 说明：JSON output parser that extracts JSON from LLM text output.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object parse(Object inputs)` | `Object` | - |
| `public Iterator<Object> streamParse(Iterator<?> streamingInputs)` | `Iterator<Object>` | - |

### `MarkdownContent`

- 类型：`class`
- 声明：`@Data public class MarkdownContent`
- 说明：Structured representation of Markdown content.
- 注解：`@Data`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `rawContent` | `String` | `private` | `""` | - |
| `elements` | `List<MarkdownElement>` | `private` | `new ArrayList<>()` | - |
| `headers` | `List<Map<String, Object>>` | `private` | `new ArrayList<>()` | - |
| `codeBlocks` | `List<Map<String, Object>>` | `private` | `new ArrayList<>()` | - |
| `links` | `List<Map<String, Object>>` | `private` | `new ArrayList<>()` | - |
| `images` | `List<Map<String, Object>>` | `private` | `new ArrayList<>()` | - |
| `tables` | `List<String>` | `private` | `new ArrayList<>()` | - |
| `lists` | `List<String>` | `private` | `new ArrayList<>()` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public MarkdownContent()` | - |
| `public MarkdownContent(String rawContent)` | - |

### `MarkdownElement`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class MarkdownElement`
- 说明：Single Markdown element with positional metadata.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `type` | `String` | `private` | `-` | - |
| `content` | `Map<String, Object>` | `private` | `-` | - |
| `startPos` | `int` | `private` | `-` | - |
| `endPos` | `int` | `private` | `-` | - |
| `raw` | `String` | `private` | `-` | - |

### `MarkdownElementType`

- 类型：`class`
- 声明：`public final class MarkdownElementType`
- 说明：Markdown element type constants.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `HEADER` | `String` | `public static final` | `"header"` | - |
| `CODE_BLOCK` | `String` | `public static final` | `"code_block"` | - |
| `INLINE_CODE` | `String` | `public static final` | `"inline_code"` | - |
| `LINK` | `String` | `public static final` | `"link"` | - |
| `IMAGE` | `String` | `public static final` | `"image"` | - |
| `TABLE` | `String` | `public static final` | `"table"` | - |
| `LIST` | `String` | `public static final` | `"list"` | - |
| `TEXT` | `String` | `public static final` | `"text"` | - |

### `MarkdownOutputParser`

- 类型：`class`
- 声明：`public class MarkdownOutputParser extends BaseOutputParser`
- 说明：Markdown output parser that extracts structured elements from LLM output.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object parse(Object inputs)` | `Object` | - |
| `public Iterator<Object> streamParse(Iterator<?> streamingInputs)` | `Iterator<Object>` | - |

## `com.openjiuwen.core.foundation.llm.schema`

公开类型：`21`

### `AssistantMessage`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) @JsonInclude(JsonInclude.Include.NON_NULL) public class AssistantMessage extends BaseMessage`
- 说明：Assistant message from LLM response, with optional tool calls and metadata.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`、`@JsonInclude`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `toolCalls` | `List<ToolCall>` | `private` | `-` | - |
| `usageMetadata` | `UsageMetadata` | `private` | `-` | - |
| `finishReason` | `String` | `private` | `-` | - |
| `parserContent` | `Object` | `private` | `-` | - |
| `reasoningContent` | `String` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public AssistantMessage(String content)` | Create an assistant message with string content. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getRole()` | `String` | - |
| `public static List<ToolCall> convertOpenAiToolCalls(List<Map<String, Object>> rawToolCalls)` | `List<ToolCall>` | Convert OpenAI API nested tool_calls format to flat ToolCall format. |
| `public Map<String, Object> toApiFormat()` | `Map<String, Object>` | Convert this message to OpenAI-compatible dict format for API requests. |

### `AssistantMessageChunk`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @EqualsAndHashCode(callSuper = true) @JsonInclude(JsonInclude.Include.NON_NULL) public class AssistantMessageChunk extends AssistantMessage`
- 说明：Streaming assistant message chunk with tool call fragment merging.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@EqualsAndHashCode`、`@JsonInclude`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public AssistantMessageChunk merge(AssistantMessageChunk other)` | `AssistantMessageChunk` | Merge another chunk into this one, combining content and tool call fragments. |

### `AudioGenerationResponse`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) @JsonInclude(JsonInclude.Include.NON_NULL) public class AudioGenerationResponse extends GenerationResponse`
- 说明：Audio/Speech generation response.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`、`@JsonInclude`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `audioUrl` | `String` | `private` | `-` | URL of the generated audio. |
| `audioData` | `byte[]` | `private` | `-` | Binary audio data. |
| `duration` | `Double` | `private` | `-` | Duration in seconds. |
| `format` | `String` | `private` | `"mp3"` | Audio format (mp3, wav, etc.). |

### `BaseMessage`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @JsonInclude(JsonInclude.Include.NON_NULL) public class BaseMessage`
- 说明：Base message class for LLM conversation messages.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@JsonInclude`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `role` | `String` | `private` | `-` | Message role (system, user, assistant, tool). |
| `content` | `Object` | `private` | `-` | Message content \u2014 either a plain string or a list of content parts. |
| `name` | `String` | `private` | `-` | Optional name identifier for the message sender. |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public BaseMessage(String role, String content)` | Create a message with role and string content. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getContentAsString()` | `String` | Get content as string. |
| `public List<Object> getContentAsList()` | `List<Object>` | Get content as list (for multimodal messages). |

### `BaseMessageChunk`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @EqualsAndHashCode(callSuper = true) public class BaseMessageChunk extends BaseMessage`
- 说明：Base streaming message chunk for accumulation via #merge(BaseMessageChunk).
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@EqualsAndHashCode`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public BaseMessageChunk(String role, Object content, String name)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public BaseMessageChunk merge(BaseMessageChunk other)` | `BaseMessageChunk` | Merge another chunk into this one (content concatenation). |
| `protected static Object mergeContent(Object left, Object right)` | `Object` | Merge content fields based on type compatibility. |

### `BaseModelInfo`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonInclude(JsonInclude.Include.NON_NULL) public class BaseModelInfo`
- 说明：Base model information \u2014 a simplified configuration used by higher-level components.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@JsonInclude`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `apiKey` | `String` | `private` | `""` | - |
| `apiBase` | `String` | `private` | `-` | - |
| `modelName` | `String` | `private` | `""` | - |
| `temperature` | `Double` | `private` | `0.95` | - |
| `topP` | `Double` | `private` | `0.1` | - |
| `streaming` | `boolean` | `private` | `false` | - |
| `timeout` | `int` | `private` | `60` | - |
| `extraFields` | `Map<String, Object>` | `private` | `new HashMap<>()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Map<String, Object> getExtraFields()` | `Map<String, Object>` | - |
| `public void setExtraField(String key, Object value)` | `void` | - |

### `GenerationResponse`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @JsonInclude(JsonInclude.Include.NON_NULL) public class GenerationResponse`
- 说明：Base generation response from LLM.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@JsonInclude`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `model` | `String` | `private` | `-` | Model used for generation. |

### `ImageGenerationResponse`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) @JsonInclude(JsonInclude.Include.NON_NULL) public class ImageGenerationResponse extends GenerationResponse`
- 说明：Image generation response.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`、`@JsonInclude`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `images` | `List<String>` | `private` | `-` | List of generated image URLs. |
| `imagesBase64` | `List<String>` | `private` | `-` | List of generated images in base64 encoding. |
| `created` | `Integer` | `private` | `-` | Timestamp of creation. |

### `MergeUtils`

- 类型：`class`
- 声明：`public final class MergeUtils`
- 说明：Utility class for merging streaming message chunks and parser content.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static Object mergeParserContent(Object left, Object right)` | `Object` | Intelligently merge parser_content fields. |
| `public static Map<String, Object> mergeMaps(Map<String, Object> left, Map<String, Object> right)` | `Map<String, Object>` | Recursively merge two maps. |

### `ModelClientConfig`

- 类型：`class`
- 声明：`@JsonInclude(JsonInclude.Include.NON_NULL) @JsonDeserialize(builder = ModelClientConfig.Builder.class) public class ModelClientConfig`
- 说明：Model client configuration (connection-level settings).
- 注解：`@JsonInclude`、`@JsonDeserialize`
- 嵌套公开类型：`ModelClientConfig.Builder`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `clientId` | `String` | `private final` | `-` | - |
| `clientProvider` | `String` | `private final` | `-` | - |
| `apiKey` | `String` | `private final` | `-` | - |
| `apiBase` | `String` | `private final` | `-` | - |
| `timeout` | `double` | `private final` | `-` | - |
| `maxRetries` | `int` | `private final` | `-` | - |
| `verifySsl` | `boolean` | `private final` | `-` | - |
| `sslCert` | `String` | `private final` | `-` | - |
| `extraFields` | `Map<String, Object>` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getClientId()` | `String` | - |
| `public String getClientProvider()` | `String` | - |
| `public String getApiKey()` | `String` | - |
| `public String getApiBase()` | `String` | - |
| `public double getTimeout()` | `double` | - |
| `public int getMaxRetries()` | `int` | - |
| `public boolean isVerifySsl()` | `boolean` | - |
| `public String getSslCert()` | `String` | - |
| `public Map<String, Object> getExtraFields()` | `Map<String, Object>` | - |
| `public static Builder builder()` | `Builder` | - |
| `public String toString()` | `String` | - |

### `ModelClientConfig.Builder`

- 类型：`class`
- 声明：`@JsonPOJOBuilder(withPrefix = "") public static class Builder`
- 说明：`ModelClientConfig` 的构建器，负责汇总连接配置并接收额外字段。
- 宿主类型：`ModelClientConfig`
- 注解：`@JsonPOJOBuilder`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `clientId` | `String` | `private` | `-` | - |
| `clientProvider` | `String` | `private` | `-` | - |
| `apiKey` | `String` | `private` | `-` | - |
| `apiBase` | `String` | `private` | `-` | - |
| `timeout` | `double` | `private` | `60.0` | - |
| `maxRetries` | `int` | `private` | `3` | - |
| `verifySsl` | `boolean` | `private` | `true` | - |
| `sslCert` | `String` | `private` | `-` | - |
| `extraFields` | `Map<String, Object>` | `private final` | `new HashMap<>()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Builder clientId(String clientId)` | `Builder` | - |
| `public Builder clientProvider(String clientProvider)` | `Builder` | - |
| `public Builder apiKey(String apiKey)` | `Builder` | - |
| `public Builder apiBase(String apiBase)` | `Builder` | - |
| `public Builder timeout(double timeout)` | `Builder` | - |
| `public Builder maxRetries(int maxRetries)` | `Builder` | - |
| `public Builder verifySsl(boolean verifySsl)` | `Builder` | - |
| `public Builder sslCert(String sslCert)` | `Builder` | - |
| `public Builder extraField(String key, Object value)` | `Builder` | - |
| `public ModelClientConfig build()` | `ModelClientConfig` | - |

### `ModelConfig`

- 类型：`record`
- 声明：`public record ModelConfig(String modelProvider, BaseModelInfo modelInfo)`
- 说明：Model configuration combining provider info and model info.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `modelProvider` | `String` | `private final` | `-` | - |
| `modelInfo` | `BaseModelInfo` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ModelConfig(String modelProvider)` | - |

### `ModelRequestConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonInclude(JsonInclude.Include.NON_NULL) public class ModelRequestConfig`
- 说明：Model request configuration (per-request parameters).
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@JsonInclude`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `modelName` | `String` | `private` | `""` | - |
| `temperature` | `Double` | `private` | `0.95` | - |
| `topP` | `Double` | `private` | `0.1` | - |
| `maxTokens` | `Integer` | `private` | `-` | - |
| `stop` | `String` | `private` | `-` | - |
| `extraFields` | `Map<String, Object>` | `private` | `new HashMap<>()` | Extra fields that are not part of the standard config. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Map<String, Object> getExtraFields()` | `Map<String, Object>` | - |
| `public void setExtraField(String key, Object value)` | `void` | - |

### `ProviderType`

- 类型：`enum`
- 声明：`public enum ProviderType`
- 说明：Model client provider type enumeration.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `OpenAI` | `new ProviderType("OpenAI")` | - |
| `OpenRouter` | `new ProviderType("OpenRouter")` | - |
| `SiliconFlow` | `new ProviderType("SiliconFlow")` | - |
| `DashScope` | `new ProviderType("DashScope")` | - |

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `value` | `String` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |
| `public static ProviderType fromValue(String value)` | `ProviderType` | Look up a provider type by its string value. |

### `SystemMessage`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @EqualsAndHashCode(callSuper = true) public class SystemMessage extends BaseMessage`
- 说明：System message in an LLM conversation.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@EqualsAndHashCode`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SystemMessage(String content)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getRole()` | `String` | - |

### `ToolCall`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonInclude(JsonInclude.Include.NON_NULL) public class ToolCall`
- 说明：Represents a tool call from LLM output.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@JsonInclude`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `private` | `-` | Tool call ID. |
| `type` | `String` | `private` | `"function"` | Tool call type (e.g., "function"). |
| `name` | `String` | `private` | `-` | Tool name. |
| `arguments` | `String` | `private` | `-` | Tool arguments as JSON string. |
| `index` | `Integer` | `private` | `-` | Tool call index, used to distinguish multiple tool calls in a single response. |

### `ToolMessage`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) @JsonInclude(JsonInclude.Include.NON_NULL) public class ToolMessage extends BaseMessage`
- 说明：Tool response message in an LLM conversation.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`、`@JsonInclude`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `toolCallId` | `String` | `private` | `-` | The ID of the tool call this message is responding to. |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ToolMessage(String content, String toolCallId)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getRole()` | `String` | - |

### `ToolMessageChunk`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @EqualsAndHashCode(callSuper = true) @JsonInclude(JsonInclude.Include.NON_NULL) public class ToolMessageChunk extends ToolMessage`
- 说明：Streaming tool message chunk.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@EqualsAndHashCode`、`@JsonInclude`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ToolMessageChunk merge(ToolMessageChunk other)` | `ToolMessageChunk` | Merge another tool message chunk into this one. |

### `UsageMetadata`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonInclude(JsonInclude.Include.NON_NULL) public class UsageMetadata`
- 说明：Usage metadata returned by LLM responses.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@JsonInclude`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `code` | `int` | `private` | `0` | - |
| `errMsg` | `String` | `private` | `""` | - |
| `prompt` | `String` | `private` | `""` | - |
| `taskId` | `String` | `private` | `""` | - |
| `modelName` | `String` | `private` | `""` | - |
| `totalLatency` | `double` | `private` | `0.0` | - |
| `firstTokenTime` | `String` | `private` | `""` | - |
| `requestStartTime` | `String` | `private` | `""` | - |
| `inputTokens` | `int` | `private` | `0` | - |
| `outputTokens` | `int` | `private` | `0` | - |
| `totalTokens` | `int` | `private` | `0` | - |
| `cacheTokens` | `int` | `private` | `0` | - |

### `UserMessage`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @EqualsAndHashCode(callSuper = true) public class UserMessage extends BaseMessage`
- 说明：User message in an LLM conversation.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@EqualsAndHashCode`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public UserMessage(String content)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getRole()` | `String` | - |

### `VideoGenerationResponse`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) @JsonInclude(JsonInclude.Include.NON_NULL) public class VideoGenerationResponse extends GenerationResponse`
- 说明：Video generation response.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`、`@JsonInclude`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `videoUrl` | `String` | `private` | `-` | URL of the generated video. |
| `videoData` | `byte[]` | `private` | `-` | Binary video data. |
| `duration` | `Double` | `private` | `-` | Duration in seconds. |
| `resolution` | `String` | `private` | `-` | Video resolution (e.g., "1920x1080"). |
| `format` | `String` | `private` | `"mp4"` | Video format (mp4, avi, etc.). |

## `com.openjiuwen.core.foundation.prompt`

公开类型：`1`

### `PromptTemplate`

- 类型：`class`
- 声明：`@Data @Builder public class PromptTemplate`
- 说明：Interpolatable text prompt template with configurable placeholders.
- 注解：`@Data`、`@Builder`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `name` | `String` | `private` | `""` | Template name. |
| `content` | `Object` | `private` | `""` | Template content \u2014 either a plain `String` or a `List `. |
| `placeholderPrefix` | `String` | `private` | `"{{"` | Left delimiter for placeholders. |
| `placeholderSuffix` | `String` | `private` | `"}}"` | Right delimiter for placeholders. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<BaseMessage> toMessages()` | `List<BaseMessage>` | Convert template content to a list of BaseMessages. |
| `public PromptTemplate format(Map<String, Object> keywords)` | `PromptTemplate` | Replace placeholders with the given keywords and return a new PromptTemplate. |

## `com.openjiuwen.core.foundation.prompt.assemble`

公开类型：`1`

### `PromptAssembler`

- 类型：`class`
- 声明：`public class PromptAssembler`
- 说明：Assembler that substitutes placeholders in a prompt template.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public PromptAssembler(Object promptTemplateContent, String placeholderPrefix, String placeholderSuffix, Map<String, Variable> initialVariables)` | Construct a PromptAssembler. |
| `public PromptAssembler(Object promptTemplateContent, String placeholderPrefix, String placeholderSuffix)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<String> getInputKeys()` | `List<String>` | Get all input keys needed for the template. |
| `public Object promptAssemble(Map<String, Object> kwargs)` | `Object` | Assemble the prompt by substituting placeholders with the given keyword arguments. |

## `com.openjiuwen.core.foundation.prompt.assemble.variables`

公开类型：`3`

### `DictableVariable`

- 类型：`class`
- 声明：`public class DictableVariable extends Variable`
- 说明：Variable class for processing dict or list type placeholders recursively.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public DictableVariable(Object data, String name, String prefix, String suffix)` | Construct a DictableVariable. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void update(Map<String, Object> kwargs)` | `void` | - |

### `TextableVariable`

- 类型：`class`
- 声明：`public class TextableVariable extends Variable`
- 说明：Variable class for processing string-type placeholders.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TextableVariable(String text, String name, String prefix, String suffix)` | Construct a new TextableVariable. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void update(Map<String, Object> kwargs)` | `void` | - |

### `Variable`

- 类型：`class`
- 声明：`public abstract class Variable`
- 说明：Base class for prompt template variables.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `name` | `String` | `protected` | `-` | - |
| `inputKeys` | `List<String>` | `protected` | `-` | - |
| `value` | `Object` | `protected` | `""` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected Variable(String name, List<String> inputKeys)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getName()` | `String` | - |
| `public void setName(String name)` | `void` | - |
| `public List<String> getInputKeys()` | `List<String>` | - |
| `public Object getValue()` | `Object` | - |
| `public abstract void update(Map<String, Object> kwargs)` | `void` | Update the variable value based on the given arguments. |
| `public Object eval(Map<String, Object> kwargs)` | `Object` | Validate input, update `value`, and return it. |
| `protected Map<String, Object> prepareInputs(Map<String, Object> kwargs)` | `Map<String, Object>` | Filter kwargs to only include keys that are in `inputKeys`. |

## `com.openjiuwen.core.foundation.store`

公开类型：`1`

### `StoreFactory`

- 类型：`class`
- 声明：`public final class StoreFactory`
- 说明：Factory helpers for foundation.store concrete implementations.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static BaseVectorStore createVectorStore(String storeType)` | `BaseVectorStore` | - |
| `public static BaseVectorStore createVectorStore(String storeType, Map<String, Object> options)` | `BaseVectorStore` | - |

## `com.openjiuwen.core.foundation.store.db`

公开类型：`1`

### `DefaultDbStore`

- 类型：`class`
- 声明：`public class DefaultDbStore extends BaseDbStore<DataSource>`
- 说明：Lightweight JDBC-backed default DB store.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public DefaultDbStore(String jdbcUrl)` | - |
| `public DefaultDbStore(String jdbcUrl, String username, String password)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public DataSource getEngine()` | `DataSource` | - |

## `com.openjiuwen.core.foundation.store.graph`

公开类型：`1`

### `InMemoryGraphStore`

- 类型：`class`
- 声明：`public class InMemoryGraphStore extends com.openjiuwen.core.graph.store.InMemoryStore`
- 说明：Foundation-store alias for the in-memory graph state store.

显式公开成员较少，当前源码主要通过字段访问器、继承关系或运行时约定暴露能力。

## `com.openjiuwen.core.foundation.store.kv`

公开类型：`2`

### `DbBasedKVStore`

- 类型：`class`
- 声明：`public class DbBasedKVStore extends BaseKVStore`
- 说明：JDBC-backed KV store using a simple two-column table.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public DbBasedKVStore(BaseDbStore<?> dbStore)` | - |
| `public DbBasedKVStore(BaseDbStore<?> dbStore, String tableName)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void set(String key, Object value)` | `void` | - |
| `public boolean exclusiveSet(String key, Object value, Integer expiry)` | `boolean` | - |
| `public Object get(String key)` | `Object` | - |
| `public boolean exists(String key)` | `boolean` | - |
| `public void delete(String key)` | `void` | - |
| `public Map<String, Object> getByPrefix(String prefix)` | `Map<String, Object>` | - |
| `public void deleteByPrefix(String prefix, Integer batchSize)` | `void` | - |
| `public List<Object> mget(List<String> keys)` | `List<Object>` | - |
| `public int batchDelete(List<String> keys, Integer batchSize)` | `int` | - |
| `public KVStorePipeline pipeline()` | `KVStorePipeline` | - |

### `InMemoryKVStore`

- 类型：`class`
- 声明：`public class InMemoryKVStore extends BaseKVStore`
- 说明：In-memory key-value store with optional expiry support.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void set(String key, Object value)` | `void` | - |
| `public boolean exclusiveSet(String key, Object value, Integer expiry)` | `boolean` | - |
| `public Object get(String key)` | `Object` | - |
| `public boolean exists(String key)` | `boolean` | - |
| `public void delete(String key)` | `void` | - |
| `public Map<String, Object> getByPrefix(String prefix)` | `Map<String, Object>` | - |
| `public void deleteByPrefix(String prefix, Integer batchSize)` | `void` | - |
| `public List<Object> mget(List<String> keys)` | `List<Object>` | - |
| `public int batchDelete(List<String> keys, Integer batchSize)` | `int` | - |
| `public KVStorePipeline pipeline()` | `KVStorePipeline` | - |

## `com.openjiuwen.core.foundation.store.object`

公开类型：`1`

### `LocalObjectStorageClient`

- 类型：`class`
- 声明：`public class LocalObjectStorageClient extends BaseObjectStorageClient`
- 说明：Local-filesystem implementation of the object storage contract.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public LocalObjectStorageClient(Path rootDirectory)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean uploadFile(String bucketName, String objectName, Path filePath) throws Exception` | `boolean` | - |
| `public boolean downloadFile(String bucketName, String objectName, Path filePath) throws Exception` | `boolean` | - |
| `public boolean deleteObject(String bucketName, String objectName) throws Exception` | `boolean` | - |
| `public boolean createBucket(String bucketName, String location) throws Exception` | `boolean` | - |
| `public boolean deleteBucket(String bucketName) throws Exception` | `boolean` | - |
| `public List<Map<String, Object>> listObjects(String bucketName, String objectPrefix, int maxObjects) throws Exception` | `List<Map<String, Object>>` | - |

## `com.openjiuwen.core.foundation.store.vector`

公开类型：`4`

### `ChromaVectorStore`

- 类型：`class`
- 声明：`public class ChromaVectorStore extends AbstractRetrievalVectorStoreAdapter`
- 说明：Foundation-store Chroma adapter.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ChromaVectorStore(Map<String, Object> options)` | - |

### `InMemoryVectorStore`

- 类型：`class`
- 声明：`public class InMemoryVectorStore extends AbstractRetrievalVectorStoreAdapter`
- 说明：Foundation-store in-memory vector store.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public InMemoryVectorStore()` | - |
| `public InMemoryVectorStore(Map<String, Object> options)` | - |

### `MilvusVectorStore`

- 类型：`class`
- 声明：`public class MilvusVectorStore extends AbstractRetrievalVectorStoreAdapter`
- 说明：Foundation-store Milvus adapter.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public MilvusVectorStore(Map<String, Object> options)` | - |

### `PGVectorStore`

- 类型：`class`
- 声明：`public class PGVectorStore extends AbstractRetrievalVectorStoreAdapter`
- 说明：Foundation-store PGVector adapter.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public PGVectorStore(Map<String, Object> options)` | - |

## `com.openjiuwen.core.foundation.store.vector_fields`

公开类型：`4`

### `BaseVectorFields`

- 类型：`class`
- 声明：`public final class BaseVectorFields`
- 说明：Reusable helpers for building vector collection schemas.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static CollectionSchema defaultSchema(String vectorFieldName, int dimension)` | `CollectionSchema` | - |

### `ChromaFields`

- 类型：`class`
- 声明：`public final class ChromaFields`
- 说明：Chroma-compatible field helpers.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static CollectionSchema defaultSchema(int dimension)` | `CollectionSchema` | - |

### `MilvusFields`

- 类型：`class`
- 声明：`public final class MilvusFields`
- 说明：Milvus-compatible field helpers.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static CollectionSchema defaultSchema(int dimension)` | `CollectionSchema` | - |

### `PgFields`

- 类型：`class`
- 声明：`public final class PgFields`
- 说明：PGVector-compatible field helpers.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static CollectionSchema defaultSchema(int dimension)` | `CollectionSchema` | - |

## `com.openjiuwen.core.foundation.tool`

公开类型：`2`

### `Tool`

- 类型：`class`
- 声明：`public abstract class Tool`
- 说明：Abstract base class for all tools.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `card` | `ToolCard` | `protected final` | `-` | The tool configuration card. |

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected Tool(ToolCard card)` | Construct a new tool with the given configuration card. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ToolCard getCard()` | `ToolCard` | Get the tool card. |
| `public abstract Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | `Object` | Execute the tool with provided inputs and return the final result. |
| `public Object invoke(Map<String, Object> inputs) throws Exception` | `Object` | Execute the tool with provided inputs (no extra kwargs). |
| `public abstract Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | `Iterator<Object>` | Execute the tool and stream incremental results. |
| `public Iterator<Object> stream(Map<String, Object> inputs) throws Exception` | `Iterator<Object>` | Execute the tool and stream incremental results (no extra kwargs). |

### `ToolCard`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) public class ToolCard extends BaseCard`
- 说明：Tool card \u2014 configuration / metadata for a tool.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `inputParams` | `Map<String, Object>` | `private` | `new HashMap<>()` | Input parameter schema (JSON Schema format). |
| `properties` | `Map<String, Object>` | `private` | `new HashMap<>()` | Custom properties map. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ToolInfo toolInfo()` | `ToolInfo` | Build a ToolInfo descriptor for this tool card. |

## `com.openjiuwen.core.foundation.tool.annotation`

公开类型：`1`

### `ToolDefinition`

- 类型：`annotation`
- 声明：`@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) public @interface ToolDefinition`
- 说明：Annotation-based Java equivalent of Python's `@tool` decorator.
- 注解：`@Retention`、`@Target`

显式公开成员较少，当前源码主要通过字段访问器、继承关系或运行时约定暴露能力。

## `com.openjiuwen.core.foundation.tool.function`

公开类型：`2`

### `AnnotatedToolFactory`

- 类型：`class`
- 声明：`public final class AnnotatedToolFactory`
- 说明：Factory that turns ToolDefinition-annotated methods into LocalFunctions.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static List<LocalFunction> scan(Object target)` | `List<LocalFunction>` | - |
| `public static LocalFunction fromMethod(Object target, Method method)` | `LocalFunction` | - |

### `LocalFunction`

- 类型：`class`
- 声明：`public class LocalFunction extends Tool`
- 说明：Local function tool that wraps a Java Function as a tool.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public LocalFunction(ToolCard card, Function<Map<String, Object>, Object> func)` | Create a local function tool. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | `Object` | - |
| `public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | `Iterator<Object>` | - |
| `public Function<Map<String, Object>, Object> getFunc()` | `Function<Map<String, Object>, Object>` | Get the underlying function. |

## `com.openjiuwen.core.foundation.tool.mcp`

公开类型：`4`

### `McpClient`

- 类型：`interface`
- 声明：`public interface McpClient`
- 说明：Abstract MCP client interface for communicating with MCP servers.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `boolean connect(int retryTimes, float timeout) throws Exception` | `boolean` | Connect to the MCP server. |
| `default boolean connect() throws Exception` | `boolean` | Connect with defaults (1 retry, no timeout). |
| `boolean disconnect(float timeout) throws Exception` | `boolean` | Disconnect from the MCP server. |
| `default boolean disconnect() throws Exception` | `boolean` | Disconnect with no timeout. |
| `List<Object> listTools(float timeout) throws Exception` | `List<Object>` | List all available tools on the MCP server. |
| `default List<Object> listTools() throws Exception` | `List<Object>` | List tools with no timeout. |
| `Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception` | `Object` | Call a tool on the MCP server. |
| `default Object callTool(String toolName, Map<String, Object> arguments) throws Exception` | `Object` | Call a tool with no timeout. |
| `Optional<Object> getToolInfo(String toolName, float timeout) throws Exception` | `Optional<Object>` | Get information about a specific tool. |
| `default Optional<Object> getToolInfo(String toolName) throws Exception` | `Optional<Object>` | Get tool info with no timeout. |
| `String getServerPath()` | `String` | Get the server path this client is connected to. |

### `McpServerConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonInclude(JsonInclude.Include.NON_NULL) public class McpServerConfig`
- 说明：MCP (Model Context Protocol) server configuration.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@JsonInclude`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `serverId` | `String` | `private` | `UUID.randomUUID().toString().replace("-", "")` | Unique server identifier. |
| `serverName` | `String` | `private` | `-` | Server display name. |
| `serverPath` | `String` | `private` | `-` | Server path or URL. |
| `clientType` | `String` | `private` | `"sse"` | Client type (e.g., "sse", "stdio"). |
| `params` | `Map<String, Object>` | `private` | `new HashMap<>()` | Additional parameters. |
| `authHeaders` | `Map<String, String>` | `private` | `new HashMap<>()` | Authentication headers. |
| `authQueryParams` | `Map<String, String>` | `private` | `new HashMap<>()` | Authentication query parameters. |
| `NO_TIMEOUT` | `float` | `public static final` | `-1` | Constant for no timeout. |

### `McpTool`

- 类型：`class`
- 声明：`public class McpTool extends Tool`
- 说明：MCP Tool that wraps MCP server tools for LLM function calling.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public McpTool(McpClient mcpClient, McpToolCard card)` | Create an MCP tool. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | `Object` | - |
| `public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | `Iterator<Object>` | - |

### `McpToolCard`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) public class McpToolCard extends ToolCard`
- 说明：MCP tool card with server identification.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `serverName` | `String` | `private` | `-` | Server name this tool belongs to. |
| `serverId` | `String` | `private` | `""` | Server identifier. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public McpToolInfo toolInfo()` | `McpToolInfo` | - |

## `com.openjiuwen.core.foundation.tool.mcp.client`

公开类型：`5`

### `OpenApiClient`

- 类型：`class`
- 声明：`public class OpenApiClient implements McpClient`
- 说明：OpenAPI-file backed MCP-style client.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public OpenApiClient(McpServerConfig config)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean connect(int retryTimes, float timeout) throws Exception` | `boolean` | - |
| `public boolean disconnect(float timeout)` | `boolean` | - |
| `public List<Object> listTools(float timeout)` | `List<Object>` | - |
| `public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception` | `Object` | - |
| `public Optional<Object> getToolInfo(String toolName, float timeout)` | `Optional<Object>` | - |
| `public String getServerPath()` | `String` | - |

### `PlaywrightClient`

- 类型：`class`
- 声明：`public class PlaywrightClient implements McpClient`
- 说明：Playwright MCP client that delegates to SSE or stdio depending on the configured server path.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public PlaywrightClient(McpServerConfig config)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean connect(int retryTimes, float timeout) throws Exception` | `boolean` | - |
| `public boolean disconnect(float timeout) throws Exception` | `boolean` | - |
| `public List<Object> listTools(float timeout) throws Exception` | `List<Object>` | - |
| `public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception` | `Object` | - |
| `public Optional<Object> getToolInfo(String toolName, float timeout) throws Exception` | `Optional<Object>` | - |
| `public String getServerPath()` | `String` | - |

### `SseClient`

- 类型：`class`
- 声明：`public class SseClient extends AbstractHttpMcpClient`
- 说明：Java baseline SSE MCP client.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SseClient(McpServerConfig config)` | - |

### `StdioClient`

- 类型：`class`
- 声明：`public class StdioClient implements McpClient`
- 说明：Stdio transport MCP client using content-length framed JSON-RPC.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public StdioClient(McpServerConfig config)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean connect(int retryTimes, float timeout) throws Exception` | `boolean` | - |
| `public boolean disconnect(float timeout) throws Exception` | `boolean` | - |
| `public List<Object> listTools(float timeout) throws Exception` | `List<Object>` | - |
| `public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception` | `Object` | - |
| `public Optional<Object> getToolInfo(String toolName, float timeout) throws Exception` | `Optional<Object>` | - |
| `public String getServerPath()` | `String` | - |

### `StreamableHttpClient`

- 类型：`class`
- 声明：`public class StreamableHttpClient extends AbstractHttpMcpClient`
- 说明：HTTP JSON-RPC based MCP client for streamable-http servers.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public StreamableHttpClient(McpServerConfig config)` | - |

## `com.openjiuwen.core.foundation.tool.schema`

公开类型：`2`

### `McpToolInfo`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) @JsonInclude(JsonInclude.Include.NON_NULL) public class McpToolInfo extends ToolInfo`
- 说明：MCP (Model Context Protocol) tool information extending base ToolInfo.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`、`@JsonInclude`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `serverName` | `String` | `private` | `-` | The MCP server name this tool belongs to. |

### `ToolInfo`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @JsonInclude(JsonInclude.Include.NON_NULL) public class ToolInfo`
- 说明：Tool information descriptor for LLM function calling.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@JsonInclude`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `type` | `String` | `private` | `"function"` | Tool type, defaults to "function". |
| `name` | `String` | `private` | `""` | Tool name. |
| `description` | `String` | `private` | `""` | Tool description. |
| `parameters` | `Map<String, Object>` | `private` | `Map.of()` | Parameter schema \u2014 follows JSON Schema format. |

## `com.openjiuwen.core.foundation.tool.service_api`

公开类型：`4`

### `ApiParamLocation`

- 类型：`enum`
- 声明：`public enum ApiParamLocation`
- 说明：API parameter locations based on OpenAPI specification.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `QUERY` | `new ApiParamLocation("query")` | Query parameters in URL (e.g., ?key=value). |
| `PATH` | `new ApiParamLocation("path")` | Path parameters in URL (e.g., /users/{id}). |
| `BODY` | `new ApiParamLocation("body")` | Request body parameters. |
| `HEADER` | `new ApiParamLocation("header")` | HTTP header parameters. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |
| `public static ApiParamLocation fromString(String text)` | `ApiParamLocation` | Parse a location string (case-insensitive). |

### `ApiParamMapper`

- 类型：`class`
- 声明：`public class ApiParamMapper`
- 说明：Maps input parameters to their corresponding API locations (query, path, body, header).

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ApiParamMapper(Map<String, Object> schema, Map<String, Object> defaultQueries, Map<String, Object> defaultHeaders, Map<String, Object> defaultPaths)` | Construct a new API parameter mapper. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Map<ApiParamLocation, Map<String, Object>> map(Map<String, Object> inputs, ApiParamLocation defaultLocation)` | `Map<ApiParamLocation, Map<String, Object>>` | Map input parameters to their respective API locations. |

### `RestfulApi`

- 类型：`class`
- 声明：`public class RestfulApi extends Tool`
- 说明：RESTful API tool that executes HTTP requests.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public RestfulApi(RestfulApiCard card)` | Construct a new RestfulApi tool. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | `Object` | - |
| `public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | `Iterator<Object>` | - |

### `RestfulApiCard`

- 类型：`class`
- 声明：`@Data @SuperBuilder @EqualsAndHashCode(callSuper = true) public class RestfulApiCard extends ToolCard`
- 说明：RESTful API tool card with HTTP method and URL configuration.
- 注解：`@Data`、`@SuperBuilder`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `SUPPORTED_METHODS` | `Set<String>` | `public static final` | `Set.of("POST", "GET")` | Supported HTTP methods. |
| `url` | `String` | `private final` | `-` | Restful API URL, e.g. |
| `method` | `String` | `private final` | `"POST"` | HTTP method (POST or GET). |
| `headers` | `Map<String, Object>` | `private final` | `Map.of()` | Default request headers. |
| `queries` | `Map<String, Object>` | `private final` | `Map.of()` | Default query parameters. |
| `paths` | `Map<String, Object>` | `private final` | `Map.of()` | Path parameters for URL placeholders. |
| `timeout` | `double` | `private final` | `60.0` | Request timeout in seconds. |
| `maxResponseByteSize` | `int` | `private final` | `10 * 1024 * 1024` | Maximum response size in bytes (default 10 MB). |

## `com.openjiuwen.core.foundation.tool.service_api.parser`

公开类型：`7`

### `BaseResponseDecompressor`

- 类型：`class`
- 声明：`public abstract class BaseResponseDecompressor`
- 说明：Base class for response decompressors.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public abstract boolean canDecompress(String encoding)` | `boolean` | Check if this decompressor supports the given content encoding. |
| `public abstract byte[] decompress(byte[] responseData) throws java.io.IOException` | `byte[]` | Decompress the response data. |

### `BaseResponseParser`

- 类型：`class`
- 声明：`public abstract class BaseResponseParser`
- 说明：Base class for response parsers.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public abstract boolean canParse(String contentType, int statusCode, java.util.Map<String, String> headers)` | `boolean` | Check if this parser can handle the response. |
| `public abstract Object parse(byte[] responseData, String contentType)` | `Object` | Parse the response data. |
| `protected String decodeBytes(byte[] data, String contentType)` | `String` | Decode bytes using the charset from Content-Type, defaulting to UTF-8. |
| `protected static String extractCharsetFromContentType(String contentType)` | `String` | Extract charset from Content-Type header value (e.g., "text/html; charset=utf-8"). |

### `DeflateDecompressor`

- 类型：`class`
- 声明：`public class DeflateDecompressor extends BaseResponseDecompressor`
- 说明：Deflate decompressor.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean canDecompress(String encoding)` | `boolean` | - |
| `public byte[] decompress(byte[] responseData) throws IOException` | `byte[]` | - |

### `GzipDecompressor`

- 类型：`class`
- 声明：`public class GzipDecompressor extends BaseResponseDecompressor`
- 说明：GZIP decompressor.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean canDecompress(String encoding)` | `boolean` | - |
| `public byte[] decompress(byte[] responseData) throws IOException` | `byte[]` | - |

### `JsonResponseParser`

- 类型：`class`
- 声明：`public class JsonResponseParser extends BaseResponseParser`
- 说明：JSON response parser.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean canParse(String contentType, int statusCode, Map<String, String> headers)` | `boolean` | - |
| `public Object parse(byte[] responseData, String contentType)` | `Object` | - |

### `ParserRegistry`

- 类型：`class`
- 声明：`public final class ParserRegistry`
- 说明：Registry for response parsers and decompressors (singleton).

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static ParserRegistry getInstance()` | `ParserRegistry` | Get the singleton instance. |
| `public void register(BaseResponseParser parser)` | `void` | Register a response parser. |
| `public void registerDecompressor(String encoding, BaseResponseDecompressor decompressor)` | `void` | Register a decompressor for the given encoding. |
| `public Object parse(Map<String, String> responseHeaders, byte[] responseData, int statusCode)` | `Object` | Parse the HTTP response by decompressing (if needed) and then delegating to a matching parser. |

### `TextResponseParser`

- 类型：`class`
- 声明：`public class TextResponseParser extends BaseResponseParser`
- 说明：Text response parser.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean canParse(String contentType, int statusCode, Map<String, String> headers)` | `boolean` | - |
| `public Object parse(byte[] responseData, String contentType)` | `Object` | - |

## `com.openjiuwen.core.foundation.tool.utils`

公开类型：`2`

### `CallableSchemaExtractor`

- 类型：`class`
- 声明：`public final class CallableSchemaExtractor`
- 说明：Reflection-based extractor that turns Java method signatures into JSON Schema.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static Map<String, Object> generateSchema(Method method)` | `Map<String, Object>` | - |
| `public static String extractFunctionDescription(Method method)` | `String` | - |

### `TypeSchemaExtractor`

- 类型：`class`
- 声明：`public final class TypeSchemaExtractor`
- 说明：Reflection-based schema extraction for Java types.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static Map<String, Object> extract(Type type)` | `Map<String, Object>` | - |

