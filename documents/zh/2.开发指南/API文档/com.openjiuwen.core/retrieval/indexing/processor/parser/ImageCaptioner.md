# com.openjiuwen.core.retrieval.indexing.processor.parser.ImageCaptioner

## 类 ImageCaptioner

```java
public class ImageCaptioner
```

`ImageCaptioner` 是图片说明生成辅助类，负责把本地图片复制到缓存目录，并把图片以 data URL 方式发给 `BaseModelClient` 获取简短描述。

## 公开常量

- `IMAGE_CAPTION_PROMPT`：固定提示词 `Write a short caption describing the provided image.`
- `SAVED_IMAGE_DIR`：默认图片缓存目录 `images`。

## 构造方法

### `public ImageCaptioner(BaseModelClient llmClient)`

保存模型客户端；若后续没有客户端，caption 结果会是空字符串。

### `public ImageCaptioner(BaseModelClient llmClient, Path allowedBaseDir)`

同时指定受信图片缓存根目录；环境变量给出的目标目录必须位于该根目录内。

## 公开静态方法

- `cpImage(String imageLoc)`：复制图片到环境变量 `OPENJIUWEN_SAVED_IMAGES_DIR` 指定目录，若未设置则写入 `images`。
- `cpImage(String imageLoc, String targetDir)`：复制到当前工作目录内的指定目录。
- `cpImage(String imageLoc, String targetDir, Path allowedBaseDir)`：校验源图片真实文件，并使用 `toRealPath()` 确认目标位于受信根目录内；路径穿越和根外符号链接会被拒绝。

## 公开方法

- `captionImages(List<String> imageLocs)`：逐张图片生成 caption；每个路径都会先解析为真实普通文件，无效路径返回空字符串占位。

## 内部行为

- `llmCall(String imageLoc)` 只读取通过真实普通文件校验的图片，再探测 MIME 类型、构造 OpenAI 风格多模态消息，并调用 `llmClient.invoke(...)`。
