# com.openjiuwen.core.runner.base.Tag

## class Tag

```java
public final class Tag
```

Tag type constants for categorizing and filtering resources.

## 字段

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `ALL` | `String` | `"*"` | Special tag matching all resources. |
| `GLOBAL` | `String` | `"__global__"` | Default tag for untagged resources. |
| `ACTIVE` | `String` | `"__active__"` | Active state tag. |
| `INACTIVE` | `String` | `"__inactive__"` | Inactive state tag. |

## 相关测试

- `ResourceMgrTest`
- `TagMgrTest`
