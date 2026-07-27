package org.example.nlp2dsl2sql.a2a;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link A2aMsgTexts} 单元测试。
 */
class A2aMsgTextsTest {

    /**
     * null 消息应返回空串。
     */
    @Test
    void extract_returnsEmpty_whenMsgNull() {
        assertEquals("", A2aMsgTexts.extract(null));
    }

    /**
     * 应拼接 Msg 中全部 TextBlock 文本。
     */
    @Test
    void extract_joinsTextBlocks() {
        Msg msg = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .textContent("hello")
                .build();
        String text = A2aMsgTexts.extract(msg);
        assertTrue(text.contains("hello"));
    }
}
