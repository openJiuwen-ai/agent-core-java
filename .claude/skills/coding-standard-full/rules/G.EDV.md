# G.EDV XML Security XML安全

共 3 条规则。

## `G.EDV.07 禁止使用不安全的XSLT转换XML文件` 🔴 🔴[安全] `security_standard_rule`

XSLT是一种样式转换标记语言，可以将XML数据转换为另外的XML或其他格式，如HTML网页，纯文字。因为XSLT的功能十分强大，可以导致任意代码执行，当使用TransformerFactory转换XML格式数据的时候，需要添加安全策略禁止不安全的XSLT代码执行。

**修改建议：** 使用TransformerFactory对xml进行格式转换操作时，要开启其安全防护策略，参考修复示例。

✅ **正确示例：**

##### 场景1：使用TransformerFactory转换XML格式数据需开启安全策略
- 修复示例：开启安全防护策略
```java
//create transformer after executing setFeature
public static void XsltTrans(String src, String dst, String xslt) {
    TransformerFactory tf = TransformerFactory.newInstance();
    tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
    try {

        // 转换器工厂设置黑名单，禁用一些不安全的方法，类似XXE防护
        tf.setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true);

        // 获取转换器对象实例
        Transformer transformer = tf.newTransformer(new StreamSource(xslt));

        // 进行转换
        transformer.transform(new StreamSource(src), new StreamResult(new FileOutputStream(dst)));
    } catch (Exception e) {
        LOGGER.error(e.getMessage());
    }
}
```

❌ **错误示例：**

##### 场景1：使用TransformerFactory转换XML格式数据需开启安全策略
- 错误示例：不添加安全策略。

```java
// transformer of StreamSource without setFeature
public static void XsltTrans(String src, String dst, String xslt) {
    TransformerFactory tf = TransformerFactory.newInstance();
    tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
    try {
        // 获取转换器对象实例
        /* 【POTENTIAL FLAW】XSLT是一种样式转换标记语言，可以将XML数据档转换为另外的XML或其它格式，
         * 如HTML网页，纯文字。因为XSLT的功能十分强大，可以导致任意代码执行，
         * 当使用TransformerFactory转换XML格式数据的时候，需要添加安全策略禁止不安全的XSLT代码执行。
         */
        Transformer transformer = tf.newTransformer(new StreamSource(xslt));

        // 进行转换
        transformer.transform(new StreamSource(src), new StreamResult(new FileOutputStream(dst)));
    } catch (Exception e) {
        LOGGER.error(e.getMessage());
    }
}
```

---

## `G.EDV.05 防止解析来自外部的XML导致的外部实体（XML External Entity）攻击` 🟠 🔴[安全] `security_standard_rule`

XML外部实体攻击。

XML实体包括内部实体和外部实体。外部实体格式：<!ENTITY 实体名 SYSTEM "URI">  或者 <!ENTITY 实体名 PUBLIC "public_ID" "URI"> 。Java中引入外部实体的协议包括http、https、ftp、file、jar、netdoc、mailto等。XXE漏洞发生在应用程序解析来自外部的XML数据或文件时没有禁止外部实体的加载，造成任意文件读取、内网端口扫描、内网网站攻击、DoS攻击等危害。

**修改建议：** 为了避免 XXE 注入，应对 XML 解析器进行安全配置，使它不允许将外部实体包含在传入的 XML 文档中。

✅ **正确示例：**

禁止解析外部一般实体和外部参数实体。

```java
private void parserXmlFileDisableExternalEntityes(String filePath) {
    try {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new File(filePath));
        ... // 解析xml文件中的内容
    } catch (ParserConfigurationException ex) {
        // 处理异常
    }
        ...
}

```

❌ **错误示例：**

下列示例中解析XML文件时未进行安全防护，当解析的XML文件是恶意用户精心构造的，系统会受到XXE攻击。
```java
private void parseXmlFile(String filePath) {
    try {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new File(filePath));
        ... // 解析xml文件中的内容
    } catch (ParserConfigurationException ex) {
        // 处理异常
    }
    ...
}
```

---

## `G.EDV.06 防止解析来自外部的XML导致的内部实体扩展（XML Entity Expansion）攻击` 🟠 🔴[安全] `security_standard_rule`

XML内部实体攻击。

XML内部实体格式： <!ENTITY 实体名 "实体的值"\> 。内部实体攻击比较常见的是XML Entity Expansion攻击，它主要试图通过消耗目标程序的服务器内存资源导致DoS攻击。

**修改建议：** 为了避免 XXE 注入，应对 XML 解析器进行安全配置，使它不允许将外部实体包含在传入的 XML 文档中。

✅ **正确示例：**

##### 场景1：不使用XML实体
- 修复示例：完全禁用该工厂的DTD。
```java
XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();

// 这将完全禁用该工厂的DTD
xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(new FileInputStream(fileName));
```
##### 场景2：不使用外部实体，需要使用内部实体
- 修复示例：禁止外部实体解析，且限制内部实体数量为10个以内
```java
XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();

// 禁用外部实体
xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

// 使用系统属性限制
System.setProperty("entityExpansionLimit", "10");
XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(new FileInputStream(fileName));
```

❌ **错误示例：**

##### 场景1：不使用XML实体
- 错误示例：XML解析器默认开启实体解析
```java
XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(new FileInputStream(fileName)); // POTENTIAL FLAW: 未禁止实体解析
```
##### 场景2：不使用外部实体，需要使用内部实体
- 错误示例：禁止外部实体解析，但未限制内部实体数量
```java
XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();

// POTENTIAL FLAW: 未限制内部实体数量
xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(new FileInputStream(fileName));
```

---
