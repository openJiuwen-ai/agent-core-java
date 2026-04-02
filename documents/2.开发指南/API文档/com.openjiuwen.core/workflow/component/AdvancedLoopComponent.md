# com.openjiuwen.core.workflow.component.AdvancedLoopComponent

## 接口 AdvancedLoopComponent

```java
public interface AdvancedLoopComponent extends LoopComponent
```

`AdvancedLoopComponent` 表示带有循环体子图的高级循环组件。

## 方法

| 签名 | 说明 |
| --- | --- |
| `HasDrawable getBody()` | 返回循环体的可视化对象。 |
| `void registerCallback(LoopCallback callback)` | 注册循环回调。 |
