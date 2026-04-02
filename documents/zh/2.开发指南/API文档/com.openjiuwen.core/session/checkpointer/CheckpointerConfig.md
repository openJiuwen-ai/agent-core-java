# com.openjiuwen.core.session.checkpointer.CheckpointerConfig

## 类 CheckpointerConfig

```java
public class CheckpointerConfig
```

`CheckpointerConfig` 是 `CheckpointerFactory.create(...)` 的输入对象，封装检查点类型与实现相关配置。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public CheckpointerConfig()` | 创建默认配置，`type` 设为 `in_memory`，`conf` 设为空 `HashMap`。 |
| `public CheckpointerConfig(String type, Map<String, Object> conf)` | 使用显式类型和配置构造对象；空值会回退到默认值。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getType()` | 返回检查点类型。 |
| `public void setType(String type)` | 设置检查点类型。 |
| `public Map<String, Object> getConf()` | 返回实现相关配置。 |
| `public void setConf(Map<String, Object> conf)` | 设置实现相关配置。 |

## 说明

- 默认类型是 `in_memory`。
- `conf` 只是透传给具体 `CheckpointerProvider`，键的语义由各 Provider 自行解释。
