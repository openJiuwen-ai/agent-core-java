# Multi-Modal Inference Demo

多模态示例：基于 `agent-core-java`，包含三个入口——

| 主类 | 功能 |
|---|---|
| `examples.ImageDescribeExample`（默认） | 图片理解：输入一张本地图片，视觉模型回答"这张图片描述的是什么" |
| `examples.VideoDescribeExample` | 视频理解：输入一段本地视频，视觉模型回答"这个视频描述的是什么" |
| `examples.ImageGenerationExample` | 图片生成：输入一段提示词，调用生图模型生成图片并保存到本地 |
| `examples.VideoGenerationExample` | 视频生成：输入提示词（可选首帧图），调用生视频模型生成视频并保存到本地 |

图片理解的代码风格与 `examples/kv-cache-demo` 中的 `ReActWeatherAgentExample` 一致（创建 agent → 注册本地工具 → `Runner.runAgent` 驱动 ReAct 循环）；图片注入方式参考 `examples/multi-modal-audit-demo` 的`LoadImagesTool`：工具返回 `data.multimodal` 列表（`type=image`、`data_url` 为 base64 data URI），`ReActAgent` 会自动将其转换为带 `image_url` 内容的多模态消息发给视觉模型。视频理解复用同一模式：工具返回 `type=video`、`data_url` 以 `data:video/` 开头的条目，`ReActAgent` 将其转换为带 `video_url` 内容的多模态消息（`iterMultimodalVideoItems` 链路）。图片生成直接使用 `Model.generateImage` 门面接口（不经过 agent 循环），视频生成使用`Model.generateVideo` 通用任务式链路。core 的 OpenAI 兼容链路（`OpenAIModelClient`）已原生实现生图（`POST {base}/images/generations`）与生视频（厂商无关的"创建+轮询+取片"任务引擎，端点差异由 kwargs 配置），DashScope 链路亦支持。

## 工作流程

```text
用户消息（问题 + 图片路径）
  -> ReAct 第 1 轮：模型看到路径，调用 LoadImageTool(path=...)
  -> LoadImageTool：读盘 -> 等比缩放(最长边<=1024px) -> JPEG重压缩 -> base64 data URI
     返回 data.multimodal = [{type=image, source_path, label, data_url}]
  -> ReActAgent 将 multimodal 条目转为 image_url 消息追加进上下文
  -> ReAct 第 2 轮：视觉模型看到图片，基于实际可见内容输出描述
```

要点：

- **模型必须是视觉模型**：`MODEL_NAME` 需配置为支持图片理解的多模态模型（如 `qwen-vl-plus`、`qwen3-vl-plus`、`glm-4.5v` 等），纯文本模型无法处理图片消息。视频理解入口还要求模型支持视频输入（如 `qwen3-vl-plus`、`qwen-vl-max`）。
- **图片自动压缩**：本地图片超过 1024px 最长边时等比缩放并重压缩为 JPEG（质量 0.85），解码失败时回退原始字节，避免超大图片撑爆请求体。
- **视频不做转码**：JDK 标准库无法重编码 MP4，`LoadVideoTool` 只做 10MB 大小上限校验，超限直接报错；视频整段 base64 发送，8MB 视频约产生 11MB 请求体，过大时请自行裁剪片段。
- **仅支持本地路径**：ReActAgent 注入图片时要求 `data_url` 以 `data:image/` 开头，HTTP URL 会被过滤，因此 `LoadImageTool` 只接受本地图片文件路径；视频同理，`LoadVideoTool` 只接受本地视频文件路径。

## 1. 打包安装 Core

示例依赖 `agent-core-java` 0.1.17，先在本地安装：

```bash
mvn -f "<你的 agent-core-java 仓路径>/pom.xml" clean install -DskipTests
```

## 2. 配置模型

编辑 `src/main/resources/apiconfig.json`，填入视觉模型的连接信息：

```json
{
  "API_BASE": "https://dashscope.aliyuncs.com/compatible-mode/v1",
  "API_KEY": "<your-vision-api-key>",
  "MODEL_PROVIDER": "OpenAI",
  "MODEL_NAME": "qwen-vl-plus",
  "LLM_SSL_VERIFY": "True"
}
```

也可不改动仓库文件，用系统属性或环境变量指向自己的配置副本：`-Dopenjiuwen.example.config=/abs/path.json` 或环境变量 `OPENJIWEN_API_CONFIG=/abs/path.json`。

## 3. 运行

Windows（PowerShell）：

```powershell
chcp.com 65001 > $null
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"

cd "<你的 agent-core-java 仓路径>/examples/multi-modal-inference"

# 默认图片（resources 内置 1080p.jpeg），默认问题
mvn -q exec:java

# 指定图片路径
mvn -q exec:java "-Dexec.args=C:/Users/twc/Desktop/图片/1080p.jpeg"

# 指定图片路径 + 自定义问题
mvn -q exec:java "-Dexec.args=C:/Users/twc/Desktop/图片/1080p.jpeg 图里画的是什么？"
```

Linux / macOS：

```bash
mvn -q exec:java -Dexec.args="/path/to/image.jpg"
```

图片路径解析优先级：`args[0]` → 系统属性 `openjiuwen.example.imagePath` →环境变量 `IMAGE_PATH` → **resources 内置素材**（启动时自动从 classpath 解包 `1080p.jpeg`到临时文件再交给加载工具，进程退出自动清理）。问题解析：`args[1]`（可选）→ 源码中的 `DEFAULT_QUESTION`。

运行结束后以 JSON 打印 agent 最终结果（含回答文本与对话元信息）。

## 3.1 视频理解入口（MultiModalVideoDescribeExample）

```powershell
cd "<你的 agent-core-java 仓路径>/examples/multi-modal-inference"

# 默认视频（resources 内置 video.mp4），默认问题
mvn -q exec:java "-Ddemo.mainClass=examples.VideoDescribeExample"

# 指定视频路径 + 自定义问题
mvn -q exec:java "-Ddemo.mainClass=examples.VideoDescribeExample" ^
  "-Dexec.args=C:/path/to/video.mp4 视频里发生了什么？"
```

视频路径解析优先级：`args[0]` → 系统属性 `openjiuwen.example.videoPath` →环境变量 `VIDEO_PATH` → **resources 内置素材**（自动解包 `video.mp4`到临时文件）。工作流程与图片理解一致，只是工具换成了 `LoadVideoTool`（返回 `type=video` 条目），第二轮请求里可以搜 `video_url` / `data:video/mp4;base64` 确认视频数据实际发送的位置。

## 4. 查看发给大模型的请求

demo 启动时默认调用 `UserConfig.setIsSensitive(false)` 关闭敏感过滤，这样 core 打印的`Before request chat model, LLM request params ready.` INFO 日志会带上完整的`messages` 和 `tools`——第二轮请求里可以搜 `image_url` / `data:image/jpeg;base64`，那就是图片数据实际发送的位置（约 19 万字符，控制台会刷屏，属正常现象）。

不想要完整请求日志时加开关静默：

```powershell
mvn -q exec:java "-Dopenjiuwen.example.quietLog=true"
```

也可以不改代码、用环境变量达到同样效果：`$env:IS_SENSITIVE = "false"`。注意生产环境不要关闭敏感过滤，避免提示词和密钥泄露到日志。

## 5. 图片生成入口（ImageGenerationExample）

core 的 OpenAI 兼容链路已原生实现生图（默认 `POST {base}/images/generations`，OpenAI 官方及同类风格端点可直接使用；DashScope 链路同样支持）。厂商端点差异由 demo 内置的按`API_BASE` 域名匹配的预设表处理——如 `api.minimax.cn` 自动改走 `POST /v1/image_generation`、模型覆盖为 `image-01`、从 `data.image_urls` 解析结果，并附带`aspect_ratio=1:1` / `response_format=url` / `prompt_optimizer=true` 默认值；apiconfig.json 保持最简五键即可。

保存为独立文件（如 `gen-config.json`），运行时通过 `-Dopenjiuwen.example.config` 指向它：

```powershell
cd "<你的 agent-core-java 仓路径>/examples/multi-modal-inference"

# 默认提示词 + 默认尺寸 1664*928
mvn -q exec:java "-Ddemo.mainClass=examples.ImageGenerationExample" ^
  "-Dopenjiuwen.example.config=D:/path/to/gen-config.json"

# 自定义提示词 + 尺寸
mvn -q exec:java "-Ddemo.mainClass=examples.ImageGenerationExample" ^
  "-Dopenjiuwen.example.config=D:/path/to/gen-config.json" ^
  "-Dexec.args=水墨画风格的黄山云海 1024*1024"
```

提示词解析优先级：`args[0]` → 系统属性 `openjiuwen.example.prompt` → 内置默认值；命中预设时 `args[1]` 为宽高比（→ `openjiuwen.example.aspectRatio`），未命中时为尺寸（→ `openjiuwen.example.imageSize`）；张数：`openjiuwen.example.n`；输出目录：`openjiuwen.example.outputDir` → `generated/`（相对模块目录）。生成的图片按 `generated-时间戳-序号.png` 落地并打印绝对路径。

## 6. 视频生成入口（VideoGenerationExample）

core 提供的是**厂商无关的通用任务式链路**：创建视频生成任务 → 轮询任务状态（默认间隔 5s、最长等待 600s，可用 `poll_interval_seconds` / `max_wait_seconds` kwargs 调整）→ 成功后解析视频。厂商端点差异不进 core 也不进配置文件——`VideoGenerationExample` 内置一张按 `API_BASE` 域名匹配的**预设表**（`VIDEO_PRESETS`），命中的域名以通用 kwargs（`video_body`/`video_create_path`/ `video_poll_path`/`video_result_url_path`/`video_file_retrieve_path` 等）形式传给 core；未命中的域名走 OpenAI 官方默认流程。接入新厂商 = 预设表加一行，或用`-Dopenjiuwen.example.video*` 手工传参，core 与配置文件均无需改动。

```powershell
# 文生视频（apiconfig.json 为 MiniMax 时自动命中对应预设）
mvn -q exec:java "-Ddemo.mainClass=examples.VideoGenerationExample"

# 指定提示词 + 首帧图片（图生视频，本地路径自动转 base64 data URI）
mvn -q exec:java "-Ddemo.mainClass=examples.VideoGenerationExample" ^
  "-Dexec.args=海浪拍打礁石，夕阳西下 C:/Users/twc/Desktop/图片/1080p.jpeg"
```

提示词：`args[0]` → `openjiuwen.example.prompt` → 内置默认值；首帧图片：`args[1]`（本地路径自动转 base64 data URI，http URL 原样透传）→ `openjiuwen.example.imgUrl`；分辨率/时长：预设默认值，`openjiuwen.example.resolution` / `openjiuwen.example.duration` 可覆盖。视频按 `video-时间戳.mp4` 落地到 `openjiuwen.example.outputDir`（默认 `generated/`）。
