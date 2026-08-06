package org.example.nlp2dsl2sql.a2a;

import org.example.nlp2dsl2sql.config.A2aHostModelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link QuestionComplexityClassifier} 单元测试。
 */
class QuestionComplexityClassifierTest {

    private QuestionComplexityClassifier classifier;

    /**
     * 构造带默认规则的分类器。
     */
    @BeforeEach
    void setUp() {
        A2aHostModelProperties properties = new A2aHostModelProperties();
        properties.setDefaultTier("strong");
        properties.setComplexity(new A2aHostModelProperties.Complexity());
        classifier = new QuestionComplexityClassifier(properties);
    }

    /**
     * 简单指标查询判为 fast。
     */
    @Test
    void classifiesSimpleMetricQueryAsFast() {
        assertEquals(QuestionComplexityClassifier.TIER_FAST,
                classifier.classify("六年级最高分是多少"));
        assertEquals(QuestionComplexityClassifier.TIER_FAST,
                classifier.classify("三年级的平均分"));
        assertEquals(QuestionComplexityClassifier.TIER_FAST,
                classifier.classify("五年级有多少学生"));
    }

    /**
     * 对比/明细类复合查询判为 strong。
     */
    @Test
    void classifiesComparisonAsStrong() {
        assertEquals(QuestionComplexityClassifier.TIER_STRONG,
                classifier.classify("各年级数学平均分对比"));
        assertEquals(QuestionComplexityClassifier.TIER_STRONG,
                classifier.classify("请列出六年级的明细成绩"));
        assertEquals(QuestionComplexityClassifier.TIER_STRONG,
                classifier.classify("三个年级平均分同比环比分析"));
    }

    /**
     * 含多个问号的复合问判为 strong。
     */
    @Test
    void classifiesCompoundQuestionAsStrong() {
        assertEquals(QuestionComplexityClassifier.TIER_STRONG,
                classifier.classify("六年级最高分是谁？最高分奖励是什么？"));
    }

    /**
     * 超过长度阈值的问句判为 strong。
     */
    @Test
    void classifiesLongQuestionAsStrong() {
        String longQuestion = "请分析最近一年内各个班级每一位学生在各门科目上的成绩变化趋势"
                + "并与去年同期进行详细的对比分析，同时列出成绩波动最大的前十名学生名单"
                + "以及相应的教师点评和对应的奖励政策说明文档，最后汇总成一份完整报告";
        assertEquals(QuestionComplexityClassifier.TIER_STRONG,
                classifier.classify(longQuestion));
    }

    /**
     * 空问题与未命中规则回退默认档位（strong）。
     */
    @Test
    void fallsBackToDefaultTierWhenNoRuleMatches() {
        assertEquals(QuestionComplexityClassifier.TIER_STRONG,
                classifier.classify(null));
        assertEquals(QuestionComplexityClassifier.TIER_STRONG,
                classifier.classify("   "));
        assertEquals(QuestionComplexityClassifier.TIER_STRONG,
                classifier.classify("今天天气怎么样"));
    }
}
