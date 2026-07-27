package org.example.nlp2dsl2sql.intent;

import org.example.nlp2dsl2sql.models.dto.dsl.IntentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RuleIntentClassifier} 单元测试（不调用 LLM）。
 */
class RuleIntentClassifierTest {

    private RuleIntentClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new RuleIntentClassifier();
    }

    /**
     * 含「平均分」「是多少」应命中 METRIC_QUERY。
     */
    @Test
    void tryMatch_metricQuery_whenAverageScoreQuestion() {
        Optional<IntentResult> result =
                classifier.tryMatch("三年级数学平均分是多少");

        assertTrue(result.isPresent());
        assertEquals(
                IntentResult.IntentType.METRIC_QUERY.name(),
                result.get().getIntent());
        assertConfidence(result.get());
    }

    /**
     * 含「各年级」「对比」应命中 DIMENSION_ANALYSIS。
     */
    @Test
    void tryMatch_dimensionAnalysis_whenCompareGrades() {
        Optional<IntentResult> result =
                classifier.tryMatch("各年级数学平均分对比");

        assertTrue(result.isPresent());
        assertEquals(
                IntentResult.IntentType.DIMENSION_ANALYSIS.name(),
                result.get().getIntent());
        assertConfidence(result.get());
    }

    /**
     * 含「列出」应命中 DETAIL_QUERY。
     */
    @Test
    void tryMatch_detailQuery_whenListStudents() {
        Optional<IntentResult> result =
                classifier.tryMatch("列出三年级学生成绩");

        assertTrue(result.isPresent());
        assertEquals(
                IntentResult.IntentType.DETAIL_QUERY.name(),
                result.get().getIntent());
        assertConfidence(result.get());
    }

    /**
     * 纯闲聊应命中 NON_BUSINESS。
     */
    @Test
    void tryMatch_nonBusiness_whenGreeting() {
        Optional<IntentResult> result = classifier.tryMatch("你好");

        assertTrue(result.isPresent());
        assertEquals(
                IntentResult.IntentType.NON_BUSINESS.name(),
                result.get().getIntent());
        assertConfidence(result.get());
    }

    /**
     * 同时命中 DIMENSION 与 DETAIL 应返回 empty（冲突交 LLM）。
     */
    @Test
    void tryMatch_empty_whenDimensionAndDetailConflict() {
        Optional<IntentResult> result =
                classifier.tryMatch("对比一下并列出明细");

        assertFalse(result.isPresent());
    }

    /**
     * null 输入应返回 empty。
     */
    @Test
    void tryMatch_empty_whenNull() {
        assertFalse(classifier.tryMatch(null).isPresent());
    }

    /**
     * 空串应返回 empty。
     */
    @Test
    void tryMatch_empty_whenBlank() {
        assertFalse(classifier.tryMatch("").isPresent());
        assertFalse(classifier.tryMatch("   ").isPresent());
    }

    /**
     * 断言规则命中时 confidence 约为 0.9。
     */
    private void assertConfidence(IntentResult result) {
        assertEquals(0.9, result.getConfidence(), 0.001);
        assertTrue(result.getReason().startsWith("规则命中:"));
    }
}
