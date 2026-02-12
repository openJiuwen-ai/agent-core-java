package com.openjiuwen.core.common.utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 单例模式注解
 * 
 * 从 Python singleton.py 元类转换
 * 
 * 注意：Java中单例模式的最佳实践是使用enum：
 * <pre>
 * public enum MyService {
 *     INSTANCE;
 *     
 *     public void doSomething() {
 *         // ...
 *     }
 * }
 * </pre>
 * 
 * 或者使用静态Holder模式：
 * <pre>
 * public class MyService {
 *     private MyService() {}
 *     
 *     private static class Holder {
 *         private static final MyService INSTANCE = new MyService();
 *     }
 *     
 *     public static MyService getInstance() {
 *         return Holder.INSTANCE;
 *     }
 * }
 * </pre>
 * 
 * 此注解主要用于标识单例类，实际实现建议使用enum或Holder模式
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Singleton {
    /**
     * 单例描述
     */
    String value() default "";
}


