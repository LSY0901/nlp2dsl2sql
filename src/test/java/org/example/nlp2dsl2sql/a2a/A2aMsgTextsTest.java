package org.example.nlp2dsl2sql.a2a;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
     * content 为 null 时应返回空串。
     */
    @Test
    void extract_returnsEmpty_whenContentNull() {
        Msg msg = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .textContent("ignored")
                .build()
                .withContent(null);
        assertEquals("", A2aMsgTexts.extract(msg));
    }

    /**
     * 多个 TextBlock 应无分隔符直接拼接。
     */
    @Test
    void extract_joinsTextBlocks() {
        Msg msg = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(
                        TextBlock.builder().text("hello").build(),
                        TextBlock.builder().text("world").build())
                .build();
        assertEquals("helloworld", A2aMsgTexts.extract(msg));
    }
}
