# com.openjiuwen.core.foundation.llm.schema.ModelConfig

## record ModelConfig

```java
public record ModelConfig(
        String modelProvider,
        BaseModelInfo modelInfo
)
```

Model configuration combining provider info and model info.

## Record Components

| Component | Description |
| --- | --- |
| `String modelProvider` | Record component declared on `ModelConfig`. |
| `BaseModelInfo modelInfo` | Record component declared on `ModelConfig`. |

## Constructors

| Signature | Description |
| --- | --- |
| `public ModelConfig(String modelProvider)` | Create a new `ModelConfig` instance. |
