package com.sky.chat.qa;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 置信度三档分档边界测试
 */
class ConfidenceGateTest {

    private final ConfidenceGate gate = new ConfidenceGate(0.55, 0.40);

    @Test
    void highTier() {
        assertEquals(ConfidenceGate.Tier.HIGH, gate.evaluate(0.808)); // W1 实测精确菜名分
        assertEquals(ConfidenceGate.Tier.HIGH, gate.evaluate(0.55));
    }

    @Test
    void mediumTier() {
        assertEquals(ConfidenceGate.Tier.MEDIUM, gate.evaluate(0.549));
        assertEquals(ConfidenceGate.Tier.MEDIUM, gate.evaluate(0.40));
    }

    @Test
    void lowTier() {
        assertEquals(ConfidenceGate.Tier.LOW, gate.evaluate(0.399));
        assertEquals(ConfidenceGate.Tier.LOW, gate.evaluate(0.0)); // 无召回
    }
}
