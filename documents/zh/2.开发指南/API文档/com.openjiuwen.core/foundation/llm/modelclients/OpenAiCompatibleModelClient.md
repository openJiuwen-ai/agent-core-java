# com.openjiuwen.core.foundation.llm.modelclients.OpenAiCompatibleModelClient

## 类 OpenAiCompatibleModelClient

```java
public class OpenAiCompatibleModelClient extends OpenAIModelClient
```

复用 OpenAI Compatible 接口的通用 HTTP 调用逻辑与响应解析流程。

## 字段

| 声明 | 说明 |
| --- | --- |
| `private final HttpClient httpClient` | 保存 `httpClient` 相关状态或配置。 |
| `private final class StreamingChunkIterator implements Iterator<AssistantMessageChunk> {` | 保存 `AssistantMessageChunk` 相关状态或配置。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public OpenAiCompatibleModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {` | 构造 `OpenAiCompatibleModelClient` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception {` | 发起同步模型调用。 |
| `public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception {` | 发起流式模型调用。 |
| `public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size, String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed, Map<String, Object> kwargs) throws Exception {` | 发起图像生成请求。 |
| `public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice, String languageType, Map<String, Object> kwargs) throws Exception {` | 发起语音生成请求。 |
| `public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl, String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark, String negativePrompt, Integer seed, Map<String, Object> kwargs) throws Exception {` | 发起视频生成请求。 |

## 说明

- 所有签名均以当前 Java 源码为准。
- `generateImage` 走 OpenAI 兼容的 `POST {base}/images/generations`（同类 OpenAI 风格生图端点可直接使用），响应同时解析 `data[].url` 与 `data[].b64_json`；`promptExtend`/`watermark`不映射到请求体，厂商专有参数经 `kwargs` 透传。
- `generateVideo` 为厂商无关的任务式链路：创建任务 → 轮询状态 → 解析结果，全部由 kwargs配置——`video_body`（`prompt` 默认 | `reference` 内容数组）、`video_create_path`（默认`/videos`）、`video_poll_path`（`{taskId}` 模板）、`video_result_url_path`（点分 JSON 路径，默认通用扫描 `content.video_url`/`video_url`/`url`）、`video_file_retrieve_path`（`{fileId}`模板，启用"查文件描述 → 取 `video_file_url_path` 指向的下载地址 → 下载二进制"中转流程）、`video_result_download_path`（`{taskId}` 模板，无 URL 时的二进制兜底，默认 `{pollPath}/content`）、`video_success_statuses`/`video_failure_statuses`（逗号分隔状态覆盖）、`poll_interval_seconds`（默认 5）与 `max_wait_seconds`（默认 600）。不传任何 kwargs 即OpenAI 官方 videos API 流程；剩余 kwargs 合并进创建请求体。
- `apiBase` 以 `/chat/completions`、`/images/generations`、`/videos` 等端点路径结尾时不再重复追加，生成流程的 GET 亦支持绝对 URL 直取。
