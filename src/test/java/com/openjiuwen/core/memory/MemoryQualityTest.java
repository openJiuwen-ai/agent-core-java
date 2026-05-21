/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.memory;

import com.openjiuwen.core.common.schema.Param;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.config.MemoryEngineConfig;
import com.openjiuwen.core.memory.config.MemoryScopeConfig;
import com.openjiuwen.core.retrieval.embedding.APIEmbedding;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import com.openjiuwen.spi.store.BaseDbStore;
import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.InMemoryKVStore;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * System tests for LongTermMemory with Chroma vector store, SQLite db_store.
 * <p>
 * Mirrors Python's {@code test_memory_quality} in
 * {@code tests/system_tests/memory/test_memory_quality.py}.
 */
@Disabled("system test")
class MemoryQualityTest {

    private LongTermMemory engine;
    private String scopeId;
    private String userId;
    private String resourceDir;

    @BeforeEach
    void setUp() throws Exception {
        engine = new LongTermMemory();
        byte[] cryptoKey = Base64.getDecoder().decode(System.getenv().getOrDefault("MEMORY_CRYPTO_KEY", ""));
        resourceDir = System.getenv().getOrDefault("MEMORY_RESOURCE_DIR", "./resource_dir");
        java.nio.file.Path resPath = java.nio.file.Path.of(resourceDir);
        if (!java.nio.file.Files.exists(resPath)) {
            java.nio.file.Files.createDirectories(resPath);
        }

        EmbeddingConfig embedConfig = new EmbeddingConfig(
                System.getenv().getOrDefault("EMBED_MODEL_NAME", "xx"),
                System.getenv().getOrDefault("EMBED_API_BASE", "xx"),
                System.getenv().getOrDefault("EMBED_API_KEY", "xx")
        );

        BaseKVStore kvStore = new InMemoryKVStore();
        String vectorDbType = System.getenv().getOrDefault("VECTOR_DB_TYPE", "chroma");
        VectorStore vectorStore;
        if ("chroma".equals(vectorDbType)) {
            vectorStore = com.openjiuwen.core.foundation.store.StoreFactory.createVectorStore("chroma", resourceDir);
        } else {
            String milvusUri = System.getenv().getOrDefault("MILVUS_URI", "http://localhost:19530");
            String milvusToken = System.getenv().getOrDefault("MILVUS_TOKEN", null);
            vectorStore = com.openjiuwen.core.foundation.store.StoreFactory.createVectorStore("milvus", milvusUri, milvusToken);
        }

        BaseDbStore dbStore = com.openjiuwen.core.foundation.store.db.DefaultDbStore.createSqlite(resourceDir + "/mem_test.db");

        ModelRequestConfig defaultModelCfg = new ModelRequestConfig();
        defaultModelCfg.setModel(System.getenv().getOrDefault("LLM_MODEL_NAME", ""));
        defaultModelCfg.setTemperature(0.3);
        defaultModelCfg.setTopP(0.9);
        ModelClientConfig defaultModelClientCfg = new ModelClientConfig();
        defaultModelClientCfg.setClientId("memory_test_client");
        defaultModelClientCfg.setClientProvider(System.getenv().getOrDefault("LLM_PROVIDER", "xx"));
        defaultModelClientCfg.setApiKey(System.getenv().getOrDefault("LLM_API_KEY", "xx"));
        defaultModelClientCfg.setApiBase(System.getenv().getOrDefault("LLM_API_BASE", "xx"));
        defaultModelClientCfg.setVerifySsl(false);
        defaultModelClientCfg.setTimeout(120);

        MemoryEngineConfig memEngineConfig = new MemoryEngineConfig();
        memEngineConfig.setDefaultModelCfg(defaultModelCfg);
        memEngineConfig.setDefaultModelClientCfg(defaultModelClientCfg);
        memEngineConfig.setForbiddenVariables("手机号,证件号");
        memEngineConfig.setCryptoKey(cryptoKey);

        APIEmbedding embeddingModel = new APIEmbedding(embedConfig);
        engine.registerStore(kvStore, vectorStore, dbStore, embeddingModel);
        engine.setConfig(memEngineConfig);

        scopeId = "test_memory_scope";
        userId = "test_user_id";

        MemoryScopeConfig scopeConfig = new MemoryScopeConfig();
        scopeConfig.setModelCfg(defaultModelCfg);
        scopeConfig.setModelClientCfg(defaultModelClientCfg);
        scopeConfig.setEmbeddingCfg(embedConfig);
        engine.setScopeConfig(scopeId, scopeConfig);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            engine.deleteMemByUserId(userId, scopeId);
            engine.deleteMessagesByUserAndScope(userId, scopeId);
        } catch (Exception e) {
            // ignore cleanup errors
        }
    }

    private void userMemCheck(List<BaseMessage> messages, List<MemCheckEntry> queryChecklist) throws Exception {
        AgentMemoryConfig agentCfg = new AgentMemoryConfig();
        agentCfg.setMemVariables(List.of());
        agentCfg.setEnableLongTermMem(true);
        engine.addMessages(userId, scopeId, messages, agentCfg);

        var allMemories = engine.getUserMemByPage(userId, scopeId, 50, 1);

        for (MemCheckEntry entry : queryChecklist) {
            var results = engine.searchUserMem(entry.query, 3, userId, scopeId);
            for (ContextCheck check : entry.contextChecks) {
                boolean memFound = false;
                for (var result : results) {
                    memFound = memFound || result.getMemInfo().getContent().contains(check.keyword);
                }
                if (check.shouldContain) {
                    assertTrue(memFound, "Can not find " + entry.query + "-" + check.keyword + " in memory (should contain)");
                } else {
                    assertFalse(memFound, "Found " + entry.query + "-" + check.keyword + " in memory (should not contain)");
                }
            }
        }
    }

    private void userVarCheck(List<Param> variableDefines, List<BaseMessage> messages,
                              List<VarCheckEntry> variableChecklist) throws Exception {
        AgentMemoryConfig agentCfg = new AgentMemoryConfig();
        agentCfg.setMemVariables(variableDefines);
        agentCfg.setEnableLongTermMem(false);
        engine.addMessages(userId, scopeId, messages, agentCfg);
        var variables = engine.getVariables(userId, scopeId);
        for (VarCheckEntry entry : variableChecklist) {
            if (entry.expectValue == null) {
                continue;
            }
            assertTrue(variables.containsKey(entry.name), "variable " + entry.name + " not found");
            String actualVal = String.valueOf(variables.get(entry.name));
            assertTrue(actualVal.contains(entry.expectValue),
                    "variable " + entry.name + " expected value: " + entry.expectValue + ", actual value: " + actualVal);
        }
    }

    static class ContextCheck {
        String keyword;
        boolean shouldContain;
        ContextCheck(String keyword, boolean shouldContain) {
            this.keyword = keyword;
            this.shouldContain = shouldContain;
        }
    }

    static class MemCheckEntry {
        String query;
        List<ContextCheck> contextChecks;
        MemCheckEntry(String query, List<ContextCheck> contextChecks) {
            this.query = query;
            this.contextChecks = contextChecks;
        }
    }

    static class VarCheckEntry {
        String name;
        String expectValue;
        VarCheckEntry(String name, String expectValue) {
            this.name = name;
            this.expectValue = expectValue;
        }
    }

    private static MemCheckEntry memCheck(String query, String keyword) {
        return new MemCheckEntry(query, List.of(new ContextCheck(keyword, true)));
    }

    private static MemCheckEntry memCheck(String query, List<String> keywords) {
        List<ContextCheck> checks = new ArrayList<>();
        for (String kw : keywords) {
            checks.add(new ContextCheck(kw, true));
        }
        return new MemCheckEntry(query, checks);
    }

    private static MemCheckEntry memCheck(String query, String keyword, boolean shouldContain) {
        return new MemCheckEntry(query, List.of(new ContextCheck(keyword, shouldContain)));
    }

    @Test
    void testVariable01() throws Exception {
        List<BaseMessage> messages = List.of(
                BaseMessage.builder().role("user").content("你好，我是Tom").build(),
                BaseMessage.builder().role("assistant").content("你好Tom，很高兴认识你").build(),
                BaseMessage.builder().role("user").content("我是一名数据分析师").build(),
                BaseMessage.builder().role("assistant").content("数据分析是个很有前景的领域").build()
        );
        List<Param> varDefines = List.of(
                Param.string("姓名", "用户姓名", false),
                Param.string("职业", "用户职业", false)
        );
        List<VarCheckEntry> checklist = List.of(
                new VarCheckEntry("姓名", "Tom"),
                new VarCheckEntry("职业", "数据分析师")
        );
        userVarCheck(varDefines, messages, checklist);
    }

    @Test
    void testVariable02() throws Exception {
        List<BaseMessage> messages = List.of(
                BaseMessage.builder().role("user").content("我喜欢的书籍是《悬疑小说》").build(),
                BaseMessage.builder().role("assistant").content("《悬疑小说》是一本好的书籍").build(),
                BaseMessage.builder().role("user").content("我的老婆喜欢的书籍是《时间简史》").build(),
                BaseMessage.builder().role("assistant").content("《时间简史》是一本好的书籍").build(),
                BaseMessage.builder().role("user").content("我的身份证号是123456").build(),
                BaseMessage.builder().role("assistant").content("我无法记住您的隐私信息").build()
        );
        List<Param> varDefines = List.of(
                Param.string("用户老婆喜欢的书籍", "用户老婆喜欢的书籍", false),
                Param.string("用户书籍", "用户喜欢的书籍", false),
                Param.string("用户身份证号", "用户身份证号", false)
        );
        List<VarCheckEntry> checklist = List.of(
                new VarCheckEntry("用户书籍", "悬疑小说"),
                new VarCheckEntry("用户老婆喜欢的书籍", "时间简史"),
                new VarCheckEntry("用户身份证号", null)
        );
        userVarCheck(varDefines, messages, checklist);
    }

    @Test
    void testUserMemBase() throws Exception {
        List<BaseMessage> messages = List.of(
                BaseMessage.builder().role("user").content("你好，我是Tom").build(),
                BaseMessage.builder().role("assistant").content("你好Tom，很高兴认识你").build(),
                BaseMessage.builder().role("user").content("我是一名数据分析师").build(),
                BaseMessage.builder().role("assistant").content("数据分析是个很有前景的领域").build(),
                BaseMessage.builder().role("user").content("业余时间我喜欢阅读和跑步").build(),
                BaseMessage.builder().role("assistant").content("阅读和跑步都是很好的爱好").build(),
                BaseMessage.builder().role("user").content("我的目标是成为数据科学家").build(),
                BaseMessage.builder().role("assistant").content("你一定能实现").build()
        );
        List<MemCheckEntry> checklist = List.of(
                memCheck("我是谁", "Tom"),
                memCheck("我的工作", "数据分析师"),
                memCheck("推荐运动", "跑步")
        );
        userMemCheck(messages, checklist);
    }

    @Test
    void testUserMemCheckNewConflict() throws Exception {
        List<BaseMessage> messages = List.of(
                BaseMessage.builder().role("user").content("你好，我是Tom，哦，不对，我是Tim").build(),
                BaseMessage.builder().role("assistant").content("你好Tim，很高兴认识你").build()
        );
        List<MemCheckEntry> checklist = List.of(
                new MemCheckEntry("我是谁", List.of(
                        new ContextCheck("Tim", true),
                        new ContextCheck("Tom", false)
                ))
        );
        userMemCheck(messages, checklist);
    }

    @Test
    void testUserMemNotSelf() throws Exception {
        List<BaseMessage> messages = List.of(
                BaseMessage.builder().role("user").content("你好，我是Tom").build(),
                BaseMessage.builder().role("assistant").content("你好Tom，很高兴认识你").build(),
                BaseMessage.builder().role("user").content("我是一名硬件工程师").build(),
                BaseMessage.builder().role("assistant").content("硬件工程师是个很有前景的职业").build(),
                BaseMessage.builder().role("user").content("我有个朋友叫宋朝").build(),
                BaseMessage.builder().role("assistant").content("你的朋友叫宋朝这个名字挺有意思的").build()
        );
        List<MemCheckEntry> checklist = List.of(
                memCheck("我是谁", "Tom"),
                memCheck("我的工作", "硬件工程师")
        );
        userMemCheck(messages, checklist);

        List<BaseMessage> messages2 = List.of(
                BaseMessage.builder().role("user").content("他是一名软件工程师").build(),
                BaseMessage.builder().role("assistant").content("软件工程师也是个很有前景的职业").build(),
                BaseMessage.builder().role("user").content("他业余时间喜欢阅读和跑步").build(),
                BaseMessage.builder().role("assistant").content("阅读和跑步都是很好的爱好").build()
        );
        List<MemCheckEntry> checklist2 = List.of(
                memCheck("宋朝是谁", List.of("朋友", "软件工程师"))
        );
        userMemCheck(messages2, checklist2);
    }

    @Test
    void testUserMemUpdate() throws Exception {
        List<BaseMessage> messages = List.of(
                BaseMessage.builder().role("user").content("你好，我是Tom").build(),
                BaseMessage.builder().role("assistant").content("你好Tom，很高兴认识你").build(),
                BaseMessage.builder().role("user").content("我是一名硬件工程师").build(),
                BaseMessage.builder().role("assistant").content("硬件工程师是个很有前景的职业").build(),
                BaseMessage.builder().role("user").content("业余时间我喜欢阅读和跑步").build(),
                BaseMessage.builder().role("assistant").content("阅读和跑步都是很好的爱好").build()
        );
        List<MemCheckEntry> checklist = List.of(
                memCheck("我的工作", "硬件工程师"),
                memCheck("我的爱好", List.of("阅读", "跑步"))
        );
        userMemCheck(messages, checklist);

        List<BaseMessage> messages2 = List.of(
                BaseMessage.builder().role("user").content("我转行成为了一名软件工程师").build(),
                BaseMessage.builder().role("assistant").content("恭喜你成功转行").build(),
                BaseMessage.builder().role("user").content("我现在不爱跑步了").build(),
                BaseMessage.builder().role("assistant").content("跑步是很好的爱好，建议要坚持").build()
        );
        List<MemCheckEntry> checklist2 = List.of(
                memCheck("我的工作", "软件工程师"),
                memCheck("不喜欢", "跑步")
        );
        userMemCheck(messages2, checklist2);
    }

    @Test
    void testUserMemReference() throws Exception {
        List<BaseMessage> messages = List.of(
                BaseMessage.builder().role("user").content("苹果的营养成分分析").build(),
                BaseMessage.builder().role("assistant").content("苹果是一种常见的水果，营养成分丰富且易于消化").build()
        );
        List<MemCheckEntry> checklist = List.of(
                memCheck("水果", "苹果", false)
        );
        userMemCheck(messages, checklist);

        List<BaseMessage> messages2 = List.of(
                BaseMessage.builder().role("user").content("我比较喜欢吃它").build(),
                BaseMessage.builder().role("assistant").content("那真是太棒了").build()
        );
        List<MemCheckEntry> checklist2 = List.of(
                memCheck("水果", List.of("用户", "苹果"))
        );
        userMemCheck(messages2, checklist2);
    }

    @Test
    void testUserMemEpisodic() throws Exception {
        List<BaseMessage> messages = List.of(
                BaseMessage.builder().role("user").content("你好，我是Tom").build(),
                BaseMessage.builder().role("assistant").content("你好Tom，很高兴认识你").build(),
                BaseMessage.builder().role("user").content("我昨天去了北京旅游").build(),
                BaseMessage.builder().role("assistant").content("北京是个很棒的地方").build(),
                BaseMessage.builder().role("user").content("我参观了故宫博物院").build(),
                BaseMessage.builder().role("assistant").content("故宫是中国文化的瑰宝").build(),
                BaseMessage.builder().role("user").content("今天我买了一本新书").build(),
                BaseMessage.builder().role("assistant").content("阅读是个好习惯").build()
        );
        List<MemCheckEntry> checklist = List.of(
                memCheck("北京旅游", List.of("用户", "北京", "故宫博物院")),
                memCheck("买了什么", List.of("用户", "新书"))
        );
        userMemCheck(messages, checklist);
    }

    @Test
    void testUserMemEpisodicConflictReal() throws Exception {
        List<BaseMessage> messages = List.of(
                BaseMessage.builder().role("user").content("你好，我是Tom").build(),
                BaseMessage.builder().role("assistant").content("你好Tom，很高兴认识你").build(),
                BaseMessage.builder().role("user").content("我上周和家人一起搬到了北京居住").build(),
                BaseMessage.builder().role("assistant").content("北京是个很棒的城市").build(),
                BaseMessage.builder().role("user").content("昨天我正式加入了A公司开始工作").build(),
                BaseMessage.builder().role("assistant").content("A公司是家很好的公司").build()
        );
        List<MemCheckEntry> checklist = List.of(
                memCheck("我住在哪里", "北京"),
                memCheck("我在哪里工作", "A公司"),
                memCheck("搬家", List.of("用户", "北京", "家人")),
                memCheck("入职", List.of("用户", "A公司"))
        );
        userMemCheck(messages, checklist);

        List<BaseMessage> messages2 = List.of(
                BaseMessage.builder().role("user").content("昨天我和同事一起搬到了上海定居").build(),
                BaseMessage.builder().role("assistant").content("上海也是个很棒的城市").build(),
                BaseMessage.builder().role("user").content("今天我成功跳槽到了B公司并完成了入职手续").build(),
                BaseMessage.builder().role("assistant").content("恭喜你有了新的工作机会").build()
        );
        List<MemCheckEntry> checklist2 = List.of(
                memCheck("我住在哪里", "上海"),
                memCheck("我在哪里工作", "B公司"),
                memCheck("最近搬家", List.of("用户", "上海", "同事")),
                memCheck("新工作", List.of("用户", "B公司", "入职手续"))
        );
        userMemCheck(messages2, checklist2);
    }

    @Test
    void testUserMemEpisodicConflictFalse() throws Exception {
        List<BaseMessage> messages = List.of(
                BaseMessage.builder().role("user").content("你好，我是Tom").build(),
                BaseMessage.builder().role("assistant").content("你好Tom，很高兴认识你").build(),
                BaseMessage.builder().role("user").content("我今天中午吃了面条").build(),
                BaseMessage.builder().role("assistant").content("面条是不错的午餐选择").build(),
                BaseMessage.builder().role("user").content("晚上我吃了米饭").build(),
                BaseMessage.builder().role("assistant").content("米饭是很常见的主食").build()
        );
        List<MemCheckEntry> checklist = List.of(
                memCheck("中午吃了什么", "面条"),
                memCheck("晚上吃了什么", "米饭")
        );
        userMemCheck(messages, checklist);

        List<BaseMessage> messages2 = List.of(
                BaseMessage.builder().role("user").content("今天上午我去看了电影").build(),
                BaseMessage.builder().role("assistant").content("看电影是很好的娱乐活动").build(),
                BaseMessage.builder().role("user").content("今天下午我去逛了公园").build(),
                BaseMessage.builder().role("assistant").content("逛公园很放松身心").build()
        );
        List<MemCheckEntry> checklist2 = List.of(
                memCheck("今天上午做了什么", "电影"),
                memCheck("今天下午做了什么", "公园")
        );
        userMemCheck(messages2, checklist2);
    }

    @Test
    void testUserMemSemantic() throws Exception {
        List<BaseMessage> messages = List.of(
                BaseMessage.builder().role("user").content("你好，我是Tom").build(),
                BaseMessage.builder().role("assistant").content("你好Tom，很高兴认识你").build(),
                BaseMessage.builder().role("user").content("地球是太阳系中的第三颗行星").build(),
                BaseMessage.builder().role("assistant").content("是的，地球是我们的家园").build(),
                BaseMessage.builder().role("user").content("水的化学式是H2O").build(),
                BaseMessage.builder().role("assistant").content("没错，这是水的化学表达式").build(),
                BaseMessage.builder().role("user").content("Python是一种流行的编程语言").build(),
                BaseMessage.builder().role("assistant").content("Python确实很受欢迎").build()
        );
        List<MemCheckEntry> checklist = List.of(
                memCheck("地球位置", List.of("太阳系", "第三颗行星")),
                memCheck("水的化学式", "H2O"),
                memCheck("Python是什么", "编程语言")
        );
        userMemCheck(messages, checklist);
    }

    @Test
    void testUserMemMixed() throws Exception {
        List<BaseMessage> messages = List.of(
                BaseMessage.builder().role("user").content("你好，我是Tom").build(),
                BaseMessage.builder().role("assistant").content("你好Tom，很高兴认识你").build(),
                BaseMessage.builder().role("user").content("我是一名数据分析师").build(),
                BaseMessage.builder().role("assistant").content("数据分析是个很有前景的领域").build(),
                BaseMessage.builder().role("user").content("我上周参加了一个Python培训").build(),
                BaseMessage.builder().role("assistant").content("Python很适合数据分析").build(),
                BaseMessage.builder().role("user").content("Python是一种解释型编程语言").build(),
                BaseMessage.builder().role("assistant").content("是的，Python语法简洁易学").build(),
                BaseMessage.builder().role("user").content("我喜欢使用Python进行数据可视化").build(),
                BaseMessage.builder().role("assistant").content("数据可视化很重要").build()
        );
        List<MemCheckEntry> checklist = List.of(
                memCheck("我是谁", "Tom"),
                memCheck("我的职业", "数据分析师"),
                memCheck("参加了什么培训", List.of("用户", "Python")),
                memCheck("Python是什么类型的语言", "解释型编程语言"),
                memCheck("Python用途", "数据可视化")
        );
        userMemCheck(messages, checklist);
    }
}
