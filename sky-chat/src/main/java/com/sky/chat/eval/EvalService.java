package com.sky.chat.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.chat.qa.ChatService;
import com.sky.chat.qa.ConfidenceGate;
import com.sky.chat.retrieve.RetrievalService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评测跑分服务（W2 基线）
 *
 * 两种模式：
 * - retrieval：只跑检索 + 分档（不调 LLM），秒级，用于调阈值
 * - full：完整问答链路（调 LLM），判答案关键词命中与拒答正确性
 *
 * 指标：
 * - hit@5：期望来源是否出现在 Top-5（有期望来源的用例）
 * - refused：LOW 档分档拒答（retrieval 模式）/ 回答命中拒答话术（full 模式）
 * - keywordHit：答案含任一期望关键词（full 模式，非拒答用例）
 */
@Service
public class EvalService {

    private final RetrievalService retrievalService;
    private final ChatService chatService;
    private final ConfidenceGate confidenceGate;
    private final ObjectMapper objectMapper;
    private final List<EvalCase> cases;

    public EvalService(RetrievalService retrievalService, ChatService chatService,
                       ConfidenceGate confidenceGate, ObjectMapper objectMapper) throws Exception {
        this.retrievalService = retrievalService;
        this.chatService = chatService;
        this.confidenceGate = confidenceGate;
        this.objectMapper = objectMapper;
        try (InputStream in = new ClassPathResource("eval/evalset-v1.json").getInputStream()) {
            EvalSetFile file = objectMapper.readValue(in, EvalSetFile.class);
            this.cases = file.cases();
        }
    }

    /** 评测集文件顶层结构 */
    record EvalSetFile(String version, String description, List<EvalCase> cases) {
    }

    // ---------- 数据结构 ----------

    public record EvalCase(String id, String question, String category,
                           List<String> expectedKeywords, List<String> expectedSourceTitles,
                           boolean shouldRefuse) {
    }

    public record CaseResult(String id, String question, String category,
                             double top1Score, String tier,
                             boolean refused, boolean expectedRefuse,
                             boolean retrievalHit, boolean keywordHit,
                             String answer, List<String> retrievedTitles) {
    }

    public record CategorySummary(String category, long total,
                                  double hitRate, double refuseCorrectRate, double keywordRate,
                                  double avgTop1) {
    }

    public record EvalReport(String mode, double highThreshold, double midThreshold,
                             long total, double hitRate, double refuseCorrectRate,
                             double keywordRate, double avgTop1,
                             List<CategorySummary> byCategory, List<CaseResult> cases) {
    }

    // ---------- 跑分 ----------

    public List<EvalCase> getCases() {
        return cases;
    }

    public EvalReport run(String mode, String categoryFilter) {
        List<CaseResult> results = new ArrayList<>();
        for (EvalCase c : cases) {
            if (categoryFilter != null && !categoryFilter.isBlank()
                    && !categoryFilter.equals(c.category())) {
                continue;
            }
            results.add("full".equalsIgnoreCase(mode) ? runFull(c) : runRetrieval(c));
        }
        return buildReport(mode, results);
    }

    /** 快速模式：只检索 + 分档，不调 LLM */
    private CaseResult runRetrieval(EvalCase c) {
        List<RetrievalService.RetrievedChunk> chunks = retrievalService.search(c.question(), 5, null);
        double top1 = chunks.isEmpty() ? 0.0 : chunks.get(0).score();
        ConfidenceGate.Tier tier = confidenceGate.evaluate(top1);
        List<String> titles = chunks.stream().map(RetrievalService.RetrievedChunk::title).toList();
        return new CaseResult(c.id(), c.question(), c.category(),
                top1, tier.name(),
                tier == ConfidenceGate.Tier.LOW, c.shouldRefuse(),
                isHit(c.expectedSourceTitles(), titles), false,
                null, titles);
    }

    /** 完整模式：走真实问答链路 */
    private CaseResult runFull(EvalCase c) {
        ChatService.ChatResult r = chatService.ask(c.question());
        boolean refused = r.answer() != null && (r.answer().contains("无法确定") || r.answer().contains("人工客服"));
        List<String> titles = r.citations().stream().map(ChatService.Citation::label).toList();
        boolean keywordHit = !refused && !c.shouldRefuse() && !c.expectedKeywords().isEmpty()
                && c.expectedKeywords().stream().anyMatch(r.answer()::contains);
        return new CaseResult(c.id(), c.question(), c.category(),
                r.top1Score(), r.tier(),
                refused, c.shouldRefuse(),
                isHit(c.expectedSourceTitles(), titles), keywordHit,
                r.answer(), titles);
    }

    /** 期望来源与实际召回标题做包含式匹配（规则段标题可能带前后缀） */
    private static boolean isHit(List<String> expected, List<String> actualTitles) {
        if (expected == null || expected.isEmpty()) {
            return true; // 无期望来源（超纲类）不算检索失败
        }
        for (String e : expected) {
            for (String a : actualTitles) {
                if (a == null || a.isEmpty()) {
                    continue;
                }
                if (a.contains(e) || e.contains(a)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------- 汇总 ----------

    private EvalReport buildReport(String mode, List<CaseResult> results) {
        Set<String> categories = results.stream().map(CaseResult::category).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        List<CategorySummary> byCategory = categories.stream()
                .map(cat -> summarize(cat, results.stream()
                        .filter(r -> r.category().equals(cat)).toList()))
                .toList();
        CategorySummary overall = summarize("ALL", results);
        return new EvalReport(mode.toLowerCase(), confidenceGate.getHighThreshold(),
                confidenceGate.getMidThreshold(), overall.total(),
                overall.hitRate(), overall.refuseCorrectRate(), overall.keywordRate(),
                overall.avgTop1(), byCategory, results);
    }

    private CategorySummary summarize(String category, List<CaseResult> rs) {
        long total = rs.size();
        double avgTop1 = rs.stream().mapToDouble(CaseResult::top1Score).average().orElse(0);
        List<CaseResult> hitScope = rs.stream()
                .filter(r -> !r.expectedRefuse()).toList(); // 有期望来源的用例才计 hit
        double hitRate = hitScope.isEmpty() ? 0
                : 100.0 * hitScope.stream().filter(CaseResult::retrievalHit).count() / hitScope.size();
        double refuseCorrect = 100.0 * rs.stream()
                .filter(r -> r.refused() == r.expectedRefuse()).count() / Math.max(total, 1);
        List<CaseResult> kwScope = rs.stream()
                .filter(r -> !r.expectedRefuse() && !r.refused()).toList();
        double kwRate = kwScope.isEmpty() ? 0
                : 100.0 * kwScope.stream().filter(CaseResult::keywordHit).count() / kwScope.size();
        return new CategorySummary(category, total, hitRate, refuseCorrect, kwRate, avgTop1);
    }
}
