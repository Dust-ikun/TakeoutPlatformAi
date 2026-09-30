package com.sky.chat.knowledge;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识分块器（W1 同步版；W8 混合检索、W10 MQ 异步化都在其上迭代）
 *
 * 分块策略（面试重点）：
 * - 菜品类：结构化切块——每个菜品一个 chunk，模板化拼接
 *   （名称/价格/口味/描述是完整信息单元，按字数切会把答案切断），
 *   并附元数据 {type, category, title}
 * - 规则类：按小节语义切块（标题做前缀），正文超长时按 300-500 字
 *   滑窗二次切分，overlap 50 字
 */
@Component
public class KnowledgeChunker {

    /** 规则类正文二次切分：目标 400 字，窗口重叠 50 字 */
    private static final int RULE_TARGET_SIZE = 400;
    private static final int RULE_OVERLAP = 50;

    private static final Pattern SECTION = Pattern.compile("(?m)^##\\s+(.+)$");
    private static final Pattern KV = Pattern.compile("^[-*]\\s*(\\S+):\\s*(.+)$",
            Pattern.MULTILINE);

    public record Chunk(String text, Map<String, Object> metadata) {
    }

    /**
     * @param docType   dish | rule
     * @param docTitle  文档标题（进 metadata，供引用溯源展示）
     * @param markdown  文档内容（## 小节结构）
     */
    public List<Chunk> chunk(String docType, String docTitle, String markdown) {
        List<Section> sections = splitSections(markdown);
        List<Chunk> result = new ArrayList<>();
        for (Section s : sections) {
            if ("dish".equals(docType)) {
                result.add(chunkDish(docTitle, s));
            } else {
                result.addAll(chunkRule(docTitle, s));
            }
        }
        return result;
    }

    /** 菜品：小节内 "- key: value" 列表 → 模板化拼接成一个信息单元 */
    private Chunk chunkDish(String docTitle, Section s) {
        Map<String, String> kv = new LinkedHashMap<>();
        kv.put("名称", s.title);
        Matcher m = KV.matcher(s.body);
        while (m.find()) {
            kv.put(m.group(1), m.group(2).trim());
        }
        StringBuilder text = new StringBuilder("菜品名称：").append(s.title);
        appendKV(text, kv, "分类");
        appendKV(text, kv, "价格");
        appendKV(text, kv, "口味");
        String desc = kv.getOrDefault("描述", "");
        if (!desc.isEmpty()) {
            text.append("；介绍：").append(desc);
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", "dish");
        meta.put("category", kv.getOrDefault("分类", docTitle));
        meta.put("title", s.title);
        meta.put("source", docTitle);
        return new Chunk(text.toString(), meta);
    }

    private void appendKV(StringBuilder sb, Map<String, String> kv, String key) {
        String v = kv.get(key);
        if (v != null && !v.isEmpty()) {
            sb.append("；").append(key).append("：").append(v);
        }
    }

    /** 规则：标题做前缀保留语义，超长正文滑窗切分（overlap 保持跨窗上下文） */
    private List<Chunk> chunkRule(String docTitle, Section s) {
        List<Chunk> chunks = new ArrayList<>();
        String body = s.body.trim();
        List<String> windows = splitByWindow(body, RULE_TARGET_SIZE, RULE_OVERLAP);
        for (int i = 0; i < windows.size(); i++) {
            String prefix = windows.size() > 1
                    ? s.title + "（第" + (i + 1) + "/" + windows.size() + "段）："
                    : s.title + "：";
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("type", "rule");
            meta.put("category", docTitle);
            meta.put("title", s.title);
            meta.put("source", docTitle);
            chunks.add(new Chunk(prefix + windows.get(i), meta));
        }
        return chunks;
    }

    /** 滑窗切分：尽量在句子边界断开，避免半个句子跨窗 */
    static List<String> splitByWindow(String text, int target, int overlap) {
        List<String> result = new ArrayList<>();
        if (text.length() <= target + overlap) {
            if (!text.isEmpty()) {
                result.add(text);
            }
            return result;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + target, text.length());
            if (end < text.length()) {
                // 在窗口尾部往回找句子边界（。！？；）
                int cut = -1;
                for (int i = end; i > start + target - 100 && i > start; i--) {
                    char c = text.charAt(i - 1);
                    if (c == '。' || c == '！' || c == '？' || c == '；') {
                        cut = i;
                        break;
                    }
                }
                if (cut > start) {
                    end = cut;
                }
            }
            result.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return result;
    }

    private record Section(String title, String body) {
    }

    private List<Section> splitSections(String markdown) {
        List<Section> sections = new ArrayList<>();
        Matcher m = SECTION.matcher(markdown);
        List<int[]> ranges = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (m.find()) {
            titles.add(m.group(1).trim());
            ranges.add(new int[]{m.end()});
        }
        for (int i = 0; i < titles.size(); i++) {
            int start = ranges.get(i)[0];
            int end = i + 1 < titles.size() ? findSectionStart(markdown, ranges.get(i + 1)[0]) : markdown.length();
            sections.add(new Section(titles.get(i), markdown.substring(start, end).trim()));
        }
        return sections;
    }

    /** 下一个小节标题行的起始位置（从匹配 end 回溯到行首） */
    private int findSectionStart(String md, int matchEnd) {
        int ls = md.lastIndexOf('\n', matchEnd);
        return ls < 0 ? 0 : ls;
    }
}
