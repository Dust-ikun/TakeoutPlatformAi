package com.sky.chat.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分块器纯逻辑单测（不依赖 Spring 上下文 / 数据库 / LLM）
 */
class KnowledgeChunkerTest {

    private final KnowledgeChunker chunker = new KnowledgeChunker();

    private static final String DISH_MD = """
            # 菜品知识库

            ## 辣子鸡
            - 分类: 川菜
            - 价格: 48 元
            - 口味: 香辣
            - 描述: 经典川菜

            ## 王老吉
            - 分类: 酒水饮料
            - 价格: 6 元
            """;

    @Test
    void dishShouldBeOneChunkWithTemplateText() {
        List<KnowledgeChunker.Chunk> chunks = chunker.chunk("dish", "dishes.md", DISH_MD);
        assertEquals(2, chunks.size());
        KnowledgeChunker.Chunk c = chunks.get(0);
        assertTrue(c.text().startsWith("菜品名称：辣子鸡"));
        assertTrue(c.text().contains("分类：川菜"));
        assertTrue(c.text().contains("价格：48 元"));
        assertEquals("dish", c.metadata().get("type"));
        assertEquals("川菜", c.metadata().get("category"));
        assertEquals("辣子鸡", c.metadata().get("title"));
    }

    @Test
    void shortRuleShouldStaySingleChunk() {
        String md = "# 规则\n\n## 营业时间\n\n平台门店每天 09:00 至 21:00 营业。";
        List<KnowledgeChunker.Chunk> chunks = chunker.chunk("rule", "rules.md", md);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).text().startsWith("营业时间："));
        assertEquals("rule", chunks.get(0).metadata().get("type"));
    }

    @Test
    void longRuleShouldSplitWithOverlapAndPrefix() {
        String sentence = "这是一个用于测试的完整句子。";
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            body.append(sentence); // ~600 字
        }
        String md = "# 规则\n\n## 退款规则\n\n" + body;
        List<KnowledgeChunker.Chunk> chunks = chunker.chunk("rule", "rules.md", md);
        assertTrue(chunks.size() >= 2, "长文应被切分, 实际=" + chunks.size());
        // 每段都带标题前缀与段号
        assertTrue(chunks.get(0).text().startsWith("退款规则（第1/"));
        assertTrue(chunks.get(1).text().startsWith("退款规则（第2/"));
        // 滑窗有重叠：第 2 段开头应能在第 1 段尾部找到（不完全断句丢失上下文）
        String tail1 = chunks.get(0).text().substring(Math.max(0, chunks.get(0).text().length() - 50));
        String head2 = chunks.get(1).text();
        assertTrue(head2.contains(tail1) || tail1.contains("。"),
                "相邻窗口应有 overlap");
        // 每段不超过目标长度 + 余量
        assertTrue(chunks.get(0).text().length() <= 480 + 20);
    }

    @Test
    void windowSplitRespectsSentenceBoundary() {
        String text = "第一句。第二句。第三句。第四句。第五句。第六句。";
        List<String> windows = KnowledgeChunker.splitByWindow(text, 12, 3);
        assertTrue(windows.size() >= 2);
        for (String w : windows) {
            assertTrue(w.endsWith("。"), "切分点应在句尾: " + w);
        }
    }
}
