package com.sky.chat.qa;

import com.sky.chat.retrieve.RetrievalService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

/**
 * 非流式问答（W1 最小链路）
 * W5 升级 SSE 流式 + 会话记忆；W2 加三档置信度拒答
 */
@Service
public class ChatService {

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

    public ChatService(ChatClient.Builder chatClientBuilder, RetrievalService retrievalService) {
        this.chatClient = chatClientBuilder
                .defaultOptions(org.springframework.ai.openai.OpenAiChatOptions.builder()
                        .model("qwen-plus")
                        .temperature(0.3)
                        .build())
                .build();
        this.retrievalService = retrievalService;
    }

    public record ChatResult(String answer, List<Citation> citations) {
    }

    public record Citation(String label, double score) {
    }

    public ChatResult ask(String question) {
        List<RetrievalService.RetrievedChunk> chunks = retrievalService.search(question, 5, null);
        if (chunks.isEmpty()) {
            return new ChatResult("抱歉，这个问题我暂时无法确定，已为您转接人工客服。", List.of());
        }

        StringBuilder refs = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievalService.RetrievedChunk c = chunks.get(i);
            refs.append("【资料").append(i + 1).append("】(")
                    .append(c.type()).append("-").append(c.title()).append(")\n")
                    .append(c.text()).append("\n\n");
        }

        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(question + "\n\n【参考资料】\n" + refs)
                .call()
                .content();

        // 引用列表：取 Top-K 的来源标签（W2 将基于回答实际引用做对齐）
        List<Citation> citations = IntStream.range(0, Math.min(chunks.size(), 3))
                .mapToObj(i -> new Citation(
                        chunks.get(i).type() + "-" + chunks.get(i).title(),
                        chunks.get(i).score()))
                .toList();
        return new ChatResult(answer, citations);
    }
}
