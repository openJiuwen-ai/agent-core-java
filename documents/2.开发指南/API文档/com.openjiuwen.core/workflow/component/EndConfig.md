# com.openjiuwen.core.workflow.component.EndConfig

## 类 EndConfig

```java
public class EndConfig
```

`EndConfig` 保存 `End` 组件的 `responseTemplate` 配置。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public EndConfig(String responseTemplate)` | 使用模板文本创建配置对象。 |
| `public static EndConfig fromMap(Map<String, Object> map)` | 从字典创建配置，同时兼容 `responseTemplate` 和 `response_template` 键。 |
| `public String getResponseTemplate()` | 返回模板文本。 |
