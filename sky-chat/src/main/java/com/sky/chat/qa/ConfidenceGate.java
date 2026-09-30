package com.sky.chat.qa;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 检索置信度分档（W2）
 *
 * 三档策略（面试重点）：
 * - HIGH  （top1 >= high-threshold）：资料可信，正常回答
 * - MEDIUM（mid-threshold <= top1 < high-threshold）：资料存疑，回答但附降级提示
 * - LOW   （top1 < mid-threshold）：直接拒答转人工，不调 LLM（省 token 且防幻觉）
 *
 * 阈值可配置：sky.chat.confidence.high-threshold / mid-threshold
 * 初始值 0.55 / 0.40 来自 W1 验收观察（精确菜名 top1 0.808，语义召回 0.4x），
 * W2 评测集跑分后再统一调参。
 */
@Component
public class ConfidenceGate {

    public enum Tier { HIGH, MEDIUM, LOW }

    private final double highThreshold;
    private final double midThreshold;

    public ConfidenceGate(
            @Value("${sky.chat.confidence.high-threshold:0.55}") double highThreshold,
            @Value("${sky.chat.confidence.mid-threshold:0.40}") double midThreshold) {
        this.highThreshold = highThreshold;
        this.midThreshold = midThreshold;
    }

    /** 按最高分（Top1）分档；无召回（top1=0）归 LOW */
    public Tier evaluate(double top1Score) {
        if (top1Score >= highThreshold) {
            return Tier.HIGH;
        }
        if (top1Score >= midThreshold) {
            return Tier.MEDIUM;
        }
        return Tier.LOW;
    }

    public double getHighThreshold() {
        return highThreshold;
    }

    public double getMidThreshold() {
        return midThreshold;
    }
}
