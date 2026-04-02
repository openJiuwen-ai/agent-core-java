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

## 公开静态方法

- `cpImage(String imageLoc)`：复制图片到环境变量 `OPENJIUWEN_SAVED_IMAGES_DIR` 指定目录，若未设置则写入 `images`。
- `cpImage(String imageLoc, String targetDir)`：复制到指定目录；源图不存在时抛 `IllegalArgumentException`。

## 公开方法

- `captionImages(List<String> imageLocs)`：逐张图片生成 caption；不存在的文件会返回空字符串占位。

## 内部行为

- `llmCall(String imageLoc)` 会读取图片字节、探测 MIME 类型、构造 OpenAI 风格多模态消息，并调用 `llmClient.invoke(...)`。
