package org.example.nlp2dsl2sql.a2a;

import org.example.nlp2dsl2sql.a2a.trace.HostTraceRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@DisplayName("HITL 等待决策：超时默认拒绝")
@ExtendWith(MockitoExtension.class)
class A2aSqlHitlRunnerWaitDecisionTest {

    @Mock
    private SqlQueryHitlAgentFactory hitlFactory;

    @Mock
    private HostTraceRecorder traceRecorder;

    private A2aSqlHitlRunner runner;

    @BeforeEach
    void setUp() {
        runner = new A2aSqlHitlRunner(
                hitlFactory, new A2aSqlConfirmRegistry(), traceRecorder);
    }

    @Test
    @DisplayName("用户批准返回 true 并记录 trace")
    void approvedReturnsTrue() {
        A2aHostChatContext ctx = new A2aHostChatContext("s1");
        List<String> chunks = subscribe(ctx);
        PendingSqlConfirm pending = new PendingSqlConfirm("s1", null, null);
        pending.getDecision().complete(true);

        assertThat(runner.waitDecision(pending, ctx, 1000)).isTrue();
        verify(traceRecorder).hitl("s1", true, null);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("approved=true");
    }

    @Test
    @DisplayName("用户拒绝返回 false 并记录原因")
    void deniedReturnsFalse() {
        A2aHostChatContext ctx = new A2aHostChatContext("s1");
        List<String> chunks = subscribe(ctx);
        PendingSqlConfirm pending = new PendingSqlConfirm("s1", null, null);
        pending.getDecision().complete(false);

        assertThat(runner.waitDecision(pending, ctx, 1000)).isFalse();
        verify(traceRecorder).hitl("s1", false, "user denied");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("approved=false");
    }

    @Test
    @DisplayName("等待超时返回 false，future 按拒绝完成")
    void timeoutDefaultsToDenied() {
        A2aHostChatContext ctx = new A2aHostChatContext("s1");
        List<String> chunks = subscribe(ctx);
        PendingSqlConfirm pending = new PendingSqlConfirm("s1", null, null);

        assertThat(runner.waitDecision(pending, ctx, 50)).isFalse();
        assertThat(pending.getDecision()).isCompletedWithValue(false);
        verify(traceRecorder).hitl("s1", false, "timeout");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("timeout");
    }

    private static List<String> subscribe(A2aHostChatContext ctx) {
        List<String> chunks = new ArrayList<>();
        ctx.getSseSink().asFlux().subscribe(chunks::add);
        return chunks;
    }
}
