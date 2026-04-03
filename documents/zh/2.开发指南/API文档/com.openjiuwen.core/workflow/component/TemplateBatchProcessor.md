# com.openjiuwen.core.workflow.component.TemplateBatchProcessor

## 类 TemplateBatchProcessor

```java
public class TemplateBatchProcessor
```

`TemplateBatchProcessor` 用于在多个数据源到齐后一次性触发模板渲染。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public boolean isRendered()` | 返回是否已经完成渲染。 |
| `public String render(Map<String, Object> additionalInputs, NodeSessionApi session)` | 合并输入后执行模板渲染并返回最终字符串。 |
