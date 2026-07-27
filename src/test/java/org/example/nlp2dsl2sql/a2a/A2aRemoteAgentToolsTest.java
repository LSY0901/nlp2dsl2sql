package org.example.nlp2dsl2sql.a2a;

import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.UserMessage;
import org.example.nlp2dsl2sql.config.A2aClientProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link A2aRemoteAgentTools} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class A2aRemoteAgentToolsTest {

    @Mock
    private A2aAgent sqlAgent;

    @Mock
    private A2aAgent sopAgent;

    /**
     * SQL Agent 正常返回时应包含远端文本。
     */
    @Test
    void callSqlAgent_returnsRemoteText() {
        Msg reply = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .textContent("最高分是张三")
                .build();
        when(sqlAgent.call(any(UserMessage.class))).thenReturn(Mono.just(reply));

        A2aRemoteAgentTools tools =
                new A2aRemoteAgentTools(sqlAgent, sopAgent, new A2aClientProperties());
        String out = tools.callSqlAgent("六年级最高分是谁？");
        assertTrue(out.contains("张三"));
    }

    /**
     * SOP Agent 调用失败时应返回含「失败」的可读错误。
     */
    @Test
    void callSopAgent_returnsErrorText_whenCallFails() {
        when(sopAgent.call(any(UserMessage.class)))
                .thenReturn(Mono.error(new RuntimeException("connection refused")));

        A2aRemoteAgentTools tools =
                new A2aRemoteAgentTools(sqlAgent, sopAgent, new A2aClientProperties());
        String out = tools.callSopAgent("最高分奖励是什么？");
        assertTrue(out.contains("失败") || out.contains("connection refused"));
    }
}
