# com.openjiuwen.core.retrieval.indexing.processor.parser.ImageCaptioner

## class ImageCaptioner

```java
public class ImageCaptioner
```

Lightweight image caption helper aligned with the Python retrieval parser stack.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `IMAGE_CAPTION_PROMPT` | `String` | Default value: `"Write a short caption describing the provided image."`. |
| `SAVED_IMAGE_DIR` | `String` | Default value: `"images"`. |

## Constructors

| Signature | Description |
| --- | --- |
| `public ImageCaptioner(BaseModelClient llmClient)` | Create a new `ImageCaptioner` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static String cpImage(String imageLoc)` | Copy the image into the target directory and return the saved path. |
| `public static String cpImage(String imageLoc, String targetDir)` | Copy the image into the target directory and return the saved path. |
| `public List<String> captionImages(List<String> imageLocs)` | Generate captions for the provided images. |

## Notes

- Related tests: `ImageCaptionerTest.java`.
