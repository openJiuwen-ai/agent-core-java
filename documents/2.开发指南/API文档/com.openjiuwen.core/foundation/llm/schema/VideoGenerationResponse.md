# com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse

## class VideoGenerationResponse

```java
public class VideoGenerationResponse extends GenerationResponse
```

Java API page for `VideoGenerationResponse`.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `videoUrl` | `String` | URL of the generated video. |
| `videoData` | `byte[]` | Binary video data. |
| `duration` | `Double` | Duration in seconds. |
| `resolution` | `String` | Video resolution (e.g., "1920x1080"). |
| `format` | `String` | Video format (mp4, avi, etc.). |

## Notes

- Lombok annotations generate the standard accessors, equality helpers, and/or builder methods referenced by this type.
