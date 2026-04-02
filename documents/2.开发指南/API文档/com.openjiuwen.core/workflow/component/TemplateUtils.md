# com.openjiuwen.core.workflow.component.TemplateUtils

## 类 TemplateUtils

```java
public final class TemplateUtils
```

`TemplateUtils` 提供模板拆分与渲染辅助方法。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static String renderTemplate(String template, Map<String, Object> inputs)` | 以 `{{var}}` 语法渲染模板。 |
| `public static List<String> renderTemplateToList(String template)` | 把模板拆成文本段与变量段列表。 |
