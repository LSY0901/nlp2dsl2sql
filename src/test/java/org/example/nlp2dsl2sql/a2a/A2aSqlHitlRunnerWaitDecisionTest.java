package org.example.nlp2dsl2sql.a2a;

import org.example.nlp2dsl2sql.a2a.trace.HostTraceRecord;
import org.example.nlp2dsl2sql.a2a.trace.HostTraceRecorder;
import org.example.nlp2dsl2sql.config.A2aHostTraceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HITL 等待决策：超时默认拒绝")
@ExtendWith(MockitoExtension.class)
class A2aSqlHitlRunnerWaitDecisionTest {

    @Mock
    private SqlQueryHitlAgentFactory hitlFactory;

    @Mock
    private org.example.nlp2dsl2sql.service.memory.QueryContextRewriter queryContextRewriter;

    private HostTraceRecorder traceRecorder;
    private org.example.nlp2dsl2sql.service.memory.SemanticSessionStore semanticSessionStore;
    private A2aSqlHitlRunner runner;

    @BeforeEach
    void setUp() {
        traceRecorder = new HostTraceRecorder(new A2aHostTraceProperties());
        semanticSessionStore = new org.example.nlp2dsl2sql.service.memory.SemanticSessionStore();
        runner = new A2aSqlHitlRunner(
                hitlFactory,
                new A2aSqlConfirmRegistry(),
                traceRecorder,
                semanticSessionStore,
                queryContextRewriter);
    }

    @Test
    @DisplayName("用户批准返回 true 并记录确认点")
    void approvedReturnsTrue() {
        A2aHostChatContext ctx = new A2aHostChatContext("s1");
        traceRecorder.start("s1", "q");
        List<String> chunks = subscribe(ctx);
        PendingSqlConfirm pending = new PendingSqlConfirm("s1", null, null);
        pending.getDecision().complete(true);

        assertThat(runner.waitDecision(pending, ctx, 1000)).isTrue();
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("approved=true");
        assertThat(confirmStep().getDetail()).doesNotContain("denied");
        assertThat(waitStep().getDetail()).isEqualTo("approved");
    }

    @Test
    @DisplayName("用户拒绝返回 false 并记录原因")
    void deniedReturnsFalse() {
        A2aHostChatContext ctx = new A2aHostChatContext("s1");
        traceRecorder.start("s1", "q");
        List<String> chunks = subscribe(ctx);
        PendingSqlConfirm pending = new PendingSqlConfirm("s1", null, null);
        pending.getDecision().complete(false);

        assertThat(runner.waitDecision(pending, ctx, 1000)).isFalse();
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("approved=false");
        assertThat(confirmStep().getDetail()).contains("user denied");
        assertThat(waitStep().getDetail()).isEqualTo("user denied");
    }

    @Test
    @DisplayName("等待超时返回 false，future 按拒绝完成")
    void timeoutDefaultsToDenied() {
        A2aHostChatContext ctx = new A2aHostChatContext("s1");
        traceRecorder.start("s1", "q");
        List<String> chunks = subscribe(ctx);
        PendingSqlConfirm pending = new PendingSqlConfirm("s1", null, null);

        assertThat(runner.waitDecision(pending, ctx, 50)).isFalse();
        assertThat(pending.getDecision()).isCompletedWithValue(false);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("timeout");
        assertThat(confirmStep().getDetail()).contains("timeout");
        assertThat(waitStep().getDetail()).isEqualTo("timeout");
        assertThat(waitStep().getDurationMs()).isGreaterThanOrEqualTo(0);
    }

    private HostTraceRecord.Step confirmStep() {
        return stepByName("sql-confirm");
    }

    private HostTraceRecord.Step waitStep() {
        return stepByName("hitl-wait");
    }

    private HostTraceRecord.Step stepByName(String name) {
        return traceRecorder.get("s1").getSteps().stream()
                .filter(s -> name.equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺失 step: " + name));
    }

    private static List<String> subscribe(A2aHostChatContext ctx) {
        List<String> chunks = new ArrayList<>();
        ctx.getSseSink().asFlux().subscribe(chunks::add);
        return chunks;
    }
}
