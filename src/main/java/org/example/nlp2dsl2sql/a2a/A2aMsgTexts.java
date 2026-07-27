package org.example.nlp2dsl2sql.a2a;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;

/**
 * 从 AgentScope Msg 中提取纯文本，供 A2A tool 返回给 Host。
 */
public final class A2aMsgTexts {

    private A2aMsgTexts() {
    }

    /**
     * 提取消息中全部 TextBlock 内容并按出现顺序拼接（块之间无分隔符）。
     *
     * @param msg A2A / Agent 返回消息，可为 null
     * @return 文本；空则返回空串
     */
    public static String extract(Msg msg) {
        if (msg == null || msg.getContent() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object block : msg.getContent()) {
            if (block instanceof TextBlock tb) {
                String t = tb.getText();
                if (t != null && !t.isEmpty()) {
                    sb.append(t);
                }
            }
        }
        return sb.toString();
    }
}
