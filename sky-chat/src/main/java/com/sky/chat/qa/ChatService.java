package com.sky.chat.qa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.chat.retrieve.RetrievalService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.IntStream;

/**
 * 问答服务
 * W1 非流式最小链路 → W2 置信度三档拒答 → W3 多轮会话记忆 + SSE 流式
 *
 * 会话记忆：MessageWindowChatMemory（滑动窗口，进程内），按 conversationId 隔离，
 * 由 MessageChatMemoryAdvisor 在调用前后自动读写历史。
 * 已知限制：进程内存存储，重启即失、会话数无上限（后续可迁 Redis/PG）。
 *
 * SSE 事件协议：meta（分档/引用）→ delta*N（增量 token）→ done。
 */
@Service
public class ChatService {

    public static final String REFUSE_MESSAGE = "抱歉，这个问题我暂时无法确定，已为您转接人工客服。";
    private static final String MEDIUM_DISCLAIMER = "（以上回答所依据的资料匹配度一般，仅供参考，如需准确信息请以 APP 展示为准或转人工客服。）";

    private static final String SYSTEM_PROMPT = """
            你是"苍穹外卖"平台的智能客服小穹，语气友好、简洁。
            规则（必须遵守）：
            1. 只依据【参考资料】回答用户问题，禁止使用任何资料之外的菜品/价格/规则信息，禁止编造。
            2. 如果资料不足以回答，直接回复：抱歉，这个问题我暂时无法确定，已为您转接人工客服。
            3. 回答末尾另起一行列出依据，格式：依据：菜品-辣子鸡；规则-退款规则（只列实际用到的资料）。
            4. 涉及下单、取消订单、退款操作等写操作请求，告知用户请在 APP 订单页操作，你只能解答疑问。
            """;

    private final ChatClient chatClient;
    private final RetrievalService retrievalService;
    private final ConfidenceGate confidenceGate;
    private final ChatMemory chatMemory;
    private final ObjectMapper objectMapper;

    public ChatService(ChatClient.Builder chatClientBuilder, RetrievalService retrievalService,
                       ConfidenceGate confidenceGate, ObjectMapper objectMapper,
                       @Value("${sky.chat.memory.window-size:10}") int memoryWindowSize) {
        this.chatClient = chatClientBuilder
                .defaultOptions(org.springframework.ai.openai.OpenAiChatOptions.builder()
                        .model("qwen-plus")
                        .temperature(0.3)
                        .build())
                .build();
        this.retrievalService = retrievalService;
        this.confidenceGate = confidenceGate;
        this.objectMapper = objectMapper;
        this.chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(memoryWindowSize)
                .build();
    }

    public record ChatResult(String answer, List<Citation> citations,
                             String tier, double top1Score) {
    }

    public record Citation(String label, double score) {
    }

    /** 评测等无会话场景使用 */
    public ChatResult ask(String question) {
        return ask(null, question);
    }

    public ChatResult ask(String conversationId, String question) {
        List<RetrievalService.RetrievedChunk> chunks = retrievalService.search(question, 5, null);

        double top1 = chunks.isEmpty() ? 0.0 : chunks.get(0).score();
        ConfidenceGate.Tier tier = confidenceGate.evaluate(top1);

        if (tier == ConfidenceGate.Tier.LOW) {
            return new ChatResult(REFUSE_MESSAGE, List.of(), tier.name(), top1);
        }

        String answer = buildSpec(conversationId, question, refs(chunks))
                .call()
                .content();

        if (tier == ConfidenceGate.Tier.MEDIUM) {
            answer = answer + "\n" + MEDIUM_DISCLAIMER;
        }

        return new ChatResult(answer, topCitations(chunks), tier.name(), top1);
    }

    /**
     * SSE 流式问答。先推 meta（分档与引用，供前端先渲染状态），再逐 token 推 delta，最后 done。
     * LOW 档不调 LLM：meta + 拒答文案 + done 三个事件直接返回。
     */
    public Flux<ServerSentEvent<String>> streamAsk(String conversationId, String question) {
        List<RetrievalService.RetrievedChunk> chunks = retrievalService.search(question, 5, null);
        double top1 = chunks.isEmpty() ? 0.0 : chunks.get(0).score();
        ConfidenceGate.Tier tier = confidenceGate.evaluate(top1);
        String meta = metaJson(tier.name(), top1, topCitations(chunks));

        if (tier == ConfidenceGate.Tier.LOW) {
            return Flux.just(
                    sse("meta", meta),
                    sse("delta", REFUSE_MESSAGE),
                    sse("done", "{\"reason\":\"low_confidence\"}"));
        }

        Flux<ServerSentEvent<String>> body = buildSpec(conversationId, question, refs(chunks))
                .stream()
                .content()
                .map(token -> sse("delta", token));

        // MEDIUM 档在流尾追加降级提示，再统一收尾
        Flux<ServerSentEvent<String>> tail = tier == ConfidenceGate.Tier.MEDIUM
                ? Flux.just(sse("delta", "\n" + MEDIUM_DISCLAIMER))
                : Flux.empty();

        return Flux.concat(
                Flux.just(sse("meta", meta)),
                body,
                tail,
                Flux.just(sse("done", "{\"reason\":\"completed\"}")));
    }

    // ---------- 内部组装 ----------

    /** 挂载系统提示 + 用户消息；conversationId 非空时挂记忆 Advisor 并以参数传会话 ID（1.1.x 用法） */
    private ChatClient.ChatClientRequestSpec buildSpec(String conversationId, String question, String refs) {
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(question + "\n\n【参考资料】\n" + refs);
        if (conversationId != null && !conversationId.isBlank()) {
            spec = spec
                    .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));
        }
        return spec;
    }

    private String refs(List<RetrievalService.RetrievedChunk> chunks) {
        StringBuilder refs = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievalService.RetrievedChunk c = chunks.get(i);
            refs.append("【资料").append(i + 1).append("】(")
                    .append(c.type()).append("-").append(c.title()).append(")\n")
                    .append(c.text()).append("\n\n");
        }
        return refs.toString();
    }

    private List<Citation> topCitations(List<RetrievalService.RetrievedChunk> chunks) {
        return IntStream.range(0, Math.min(chunks.size(), 3))
                .mapToObj(i -> new Citation(
                        chunks.get(i).type() + "-" + chunks.get(i).title(),
                        chunks.get(i).score()))
                .toList();
    }

    private String metaJson(String tier, double top1, List<Citation> citations) {
        try {
            return objectMapper.writeValueAsString(new Meta(tier, top1, citations));
        } catch (Exception e) {
            return "{\"tier\":\"" + tier + "\"}";
        }
    }

    private record Meta(String tier, double top1Score, List<Citation> citations) {
    }

    private static ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.builder(data).event(event).build();
    }
}
