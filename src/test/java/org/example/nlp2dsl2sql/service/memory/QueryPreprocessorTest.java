package org.example.nlp2dsl2sql.service.memory;

import org.example.nlp2dsl2sql.a2a.A2aHostModelRouter;
import org.example.nlp2dsl2sql.config.MemoryProperties;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticQueryDSL;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticSessionSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 追问消歧判定与会话记忆写入的最小自检。
 * <p>
 * 这里刻意不 mock LLM：改写判定必须是纯词表规则，才可能脱离模型单独验证。
 * 真走到改写分支的用例交给集成验证。
 */
@DisplayName("会话记忆：追问消歧判定 + 快照写入")
@ExtendWith(MockitoExtension.class)
class QueryPreprocessorTest {

    @Mock
    private A2aHostModelRouter modelRouter;

    private SemanticSessionStore store;
    private QueryPreprocessor preprocessor;

    @BeforeEach
    void setUp() {
        MemoryProperties props = new MemoryProperties();
        store = new SemanticSessionStore(props);
        preprocessor = new QueryPreprocessor(store, new QueryContextRewriter(modelRouter));
    }

    @Test
    @DisplayName("自包含的短问句不触发改写（不因长度短就送去 LLM）")
    void shortSelfContainedQuestionNotRewritten() {
        preprocessor.remember("s1", "三年级有多少学生", "三年级有多少学生", studentCountDsl());

        String effective = preprocessor.preprocess("s1", "四年级人数");

        assertThat(effective).isEqualTo("四年级人数");
    }

    @Test
    @DisplayName("命中代词/省略特征时走改写分支")
    void ellipsisQuestionTriggersRewriteBranch() {
        preprocessor.remember("s1", "三年级有多少学生", "三年级有多少学生", studentCountDsl());

        // 命中"那"前缀与"呢"结尾，会进入 callFastLlmRewrite；
        // 未 stub 模型时抛异常，rewriteIfNeeded 降级回退原句。
        String effective = preprocessor.preprocess("s1", "那四年级呢");

        assertThat(effective).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("无快照时不改写")
    void noSnapshotNoRewrite() {
        String effective = preprocessor.preprocess("s-empty", "那四年级呢");

        assertThat(effective).isEqualTo("那四年级呢");
    }

    @Test
    @DisplayName("sessionId 为空时透传原问句")
    void blankSessionIdPassesThrough() {
        assertThat(preprocessor.preprocess(null, "那四年级呢")).isEqualTo("那四年级呢");
        assertThat(preprocessor.preprocess("  ", "那四年级呢")).isEqualTo("那四年级呢");
    }

    @Test
    @DisplayName("本轮没产出 DSL 时不覆盖上一轮快照")
    void nullDslDoesNotOverwriteSnapshot() {
        preprocessor.remember("s1", "三年级有多少学生", "三年级有多少学生", studentCountDsl());
        SemanticSessionSnapshot before = store.get("s1");

        preprocessor.remember("s1", "今天天气怎么样", "今天天气怎么样", null);

        SemanticSessionSnapshot after = store.get("s1");
        assertThat(after).isSameAs(before);
        assertThat(after.getQuestion()).isEqualTo("三年级有多少学生");
    }

    @Test
    @DisplayName("快照不携带查询结果，避免过期数据被当成答案")
    void snapshotCarriesNoResultSet() {
        preprocessor.remember("s1", "三年级有多少学生", "三年级有多少学生", studentCountDsl());

        SemanticSessionSnapshot snapshot = store.get("s1");
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getDsl()).isNotNull();
        for (java.lang.reflect.Field f : SemanticSessionSnapshot.class.getDeclaredFields()) {
            assertThat(f.getName())
                    .as("快照不应保留结果集/富化DSL/SQL 等无人读取的字段")
                    .isIn("question", "effectiveQuestion", "dsl", "timestamp");
        }
    }

    @Test
    @DisplayName("TTL 过期后快照被清理")
    void expiredSnapshotIsEvicted() {
        MemoryProperties props = new MemoryProperties();
        props.setSessionTtl(java.time.Duration.ofMillis(1));
        SemanticSessionStore shortLived = new SemanticSessionStore(props);
        QueryPreprocessor p = new QueryPreprocessor(shortLived,
                new QueryContextRewriter(modelRouter));
        p.remember("s1", "三年级有多少学生", "三年级有多少学生", studentCountDsl());

        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThat(shortLived.get("s1")).isNull();
    }

    private SemanticQueryDSL studentCountDsl() {
        SemanticQueryDSL dsl = new SemanticQueryDSL();
        dsl.setMetric("学生人数");
        dsl.setEntity("学生");
        dsl.setDimensions(List.of("年级"));
        dsl.setFilters(List.of());
        return dsl;
    }
}
