package org.example.nlp2dsl2sql.a2a;

import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.config.A2aHostModelProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 问题复杂度分类器（独立于 {@link org.example.nlp2dsl2sql.intent.RuleIntentClassifier}）。
 * <p>
 * 按关键词与长度规则判定问题应使用快模型还是强模型。
 */
@Slf4j
@Component
public class QuestionComplexityClassifier {

    /** 复杂度档位：快模型 */
    public static final String TIER_FAST = "fast";

    /** 复杂度档位：强模型 */
    public static final String TIER_STRONG = "strong";

    private final A2aHostModelProperties properties;

    /**
     * @param properties 复杂度规则配置
     */
    public QuestionComplexityClassifier(A2aHostModelProperties properties) {
        this.properties = properties;
    }

    /**
     * 判定问题复杂度对应档位。
     *
     * @param question 用户问题原文
     * @return fast / strong；空问题返回 defaultTier
     */
    public String classify(String question) {
        if (question == null || question.isBlank()) {
            return properties.getDefaultTier();
        }
        String text = question.trim();

        if (isCompound(text)) {
            log.info("[ModelRouter] 复合问题判为复杂: {}", abbreviate(text));
            return TIER_STRONG;
        }
        if (containsAny(text, keywords(properties, true))) {
            log.info("[ModelRouter] 命中复杂关键词判为复杂: {}", abbreviate(text));
            return TIER_STRONG;
        }
        if (containsAny(text, keywords(properties, false))) {
            log.info("[ModelRouter] 命中简单关键词判为简单: {}", abbreviate(text));
            return TIER_FAST;
        }
        if (text.length() > properties.getComplexity().getLengthThreshold()) {
            log.info("[ModelRouter] 超长度阈值判为复杂: {}", abbreviate(text));
            return TIER_STRONG;
        }
        return properties.getDefaultTier();
    }

    /**
     * 复合问题信号：包含多个问号。
     *
     * @param text 问题文本
     * @return 是否复合
     */
    private boolean isCompound(String text) {
        int count = 0;
        for (char c : text.toCharArray()) {
            if (c == '？' || c == '?') {
                count++;
                if (count >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断文本是否包含关键词集合中的任一词。
     */
    private boolean containsAny(String text, List<String> keywords) {
        if (keywords == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank()
                    && lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取配置中的简单/复杂关键词。
     */
    private List<String> keywords(A2aHostModelProperties properties, boolean complex) {
        if (properties.getComplexity() == null) {
            return List.of();
        }
        return complex
                ? properties.getComplexity().getComplexKeywords()
                : properties.getComplexity().getSimpleKeywords();
    }

    /**
     * 日志截断。
     */
    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 80 ? text.substring(0, 80) + "..." : text;
    }
}
