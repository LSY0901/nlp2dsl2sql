package org.example.nlp2dsl2sql.intent;

import org.example.nlp2dsl2sql.models.dto.dsl.IntentResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 高置信关键词意图规则：唯一命中才返回结果，否则 empty 交 LLM。
 */
@Component
public class RuleIntentClassifier {

    private static final double RULE_CONFIDENCE = 0.9;

    private static final Set<String> CHITCHAT = Set.of(
            "你好", "您好", "你是谁", "谢谢", "天气");
    private static final Set<String> DIMENSION = Set.of(
            "对比", "比较", "分别", "各年级", "各个", "同比", "环比");
    private static final Set<String> DETAIL = Set.of(
            "列出", "明细", "清单", "有哪些学生", "详细列表", "逐条");
    private static final Set<String> METRIC = Set.of(
            "是多少", "有多少", "平均分", "总分", "最高分", "最低分", "数量");
    private static final Set<String> BUSINESS = Set.of(
            "平均分", "总分", "最高分", "多少", "对比", "列出", "明细",
            "学生", "成绩", "年级");

    /**
     * 尝试用规则识别意图。
     *
     * @param question 用户问题
     * @return 唯一命中时的结果；未命中或冲突为空
     */
    public Optional<IntentResult> tryMatch(String question) {
        if (question == null || question.isBlank()) {
            return Optional.empty();
        }
        String text = question.trim();

        List<Hit> hits = new ArrayList<>();
        if (matchChitchat(text)) {
            hits.add(hit(IntentResult.IntentType.NON_BUSINESS, "闲聊关键词"));
        }
        String dimKw = firstKeyword(text, DIMENSION);
        if (dimKw != null) {
            hits.add(hit(IntentResult.IntentType.DIMENSION_ANALYSIS, dimKw));
        }
        String distributionKw = matchDistribution(text);
        if (distributionKw != null) {
            hits.add(hit(IntentResult.IntentType.DIMENSION_ANALYSIS, distributionKw));
        }
        String detKw = firstKeyword(text, DETAIL);
        if (detKw != null) {
            hits.add(hit(IntentResult.IntentType.DETAIL_QUERY, detKw));
        }
        String metKw = firstKeyword(text, METRIC);
        if (metKw != null) {
            hits.add(hit(IntentResult.IntentType.METRIC_QUERY, metKw));
        }

        hits.removeIf(h ->
                h.type == IntentResult.IntentType.NON_BUSINESS
                        && containsAny(text, BUSINESS));

        List<Hit> businessHits = hits.stream()
                .filter(h -> h.type != IntentResult.IntentType.NON_BUSINESS)
                .toList();
        if (isBusinessConflict(businessHits)) {
            return Optional.empty();
        }
        Hit businessHit = selectBusinessHit(businessHits);
        if (businessHit != null) {
            return Optional.of(toResult(businessHit));
        }
        if (hits.size() == 1
                && hits.get(0).type == IntentResult.IntentType.NON_BUSINESS) {
            return Optional.of(toResult(hits.get(0)));
        }
        return Optional.empty();
    }

    /**
     * 判断业务意图是否冲突（DIMENSION 与 DETAIL 同时命中）。
     */
    private boolean isBusinessConflict(List<Hit> businessHits) {
        if (businessHits.size() <= 1) {
            return false;
        }
        boolean hasDimension = hasIntentType(
                businessHits, IntentResult.IntentType.DIMENSION_ANALYSIS);
        boolean hasDetail = hasIntentType(
                businessHits, IntentResult.IntentType.DETAIL_QUERY);
        return hasDimension && hasDetail;
    }

    /**
     * 多业务命中时按优先级选取唯一意图。
     */
    private Hit selectBusinessHit(List<Hit> businessHits) {
        if (businessHits.isEmpty()) {
            return null;
        }
        if (businessHits.size() == 1) {
            return businessHits.get(0);
        }
        return businessHits.stream()
                .min(this::compareIntentPriority)
                .orElse(null);
    }

    /**
     * 意图优先级：DIMENSION &gt; DETAIL &gt; METRIC。
     */
    private int compareIntentPriority(Hit left, Hit right) {
        return Integer.compare(
                intentPriority(left.type), intentPriority(right.type));
    }

    /**
     * 返回意图优先级数值（越小越优先）。
     */
    private int intentPriority(IntentResult.IntentType type) {
        return switch (type) {
            case DIMENSION_ANALYSIS -> 0;
            case DETAIL_QUERY -> 1;
            case METRIC_QUERY -> 2;
            default -> 3;
        };
    }

    /**
     * 判断命中列表是否包含指定意图类型。
     */
    private boolean hasIntentType(
            List<Hit> hits, IntentResult.IntentType type) {
        return hits.stream().anyMatch(h -> h.type == type);
    }

    /**
     * 判断是否命中闲聊关键词。
     */
    private boolean matchChitchat(String text) {
        return firstKeyword(text, CHITCHAT) != null;
    }

    /**
     * 匹配「按…分布」类维度分析特征。
     */
    private String matchDistribution(String text) {
        if (text.contains("按") && text.contains("分布")) {
            return "按…分布";
        }
        return null;
    }

    /**
     * 返回文本中首个命中的关键词。
     */
    private String firstKeyword(String text, Set<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return keyword;
            }
        }
        return null;
    }

    /**
     * 判断文本是否包含关键词集合中的任一词。
     */
    private boolean containsAny(String text, Set<String> keywords) {
        return firstKeyword(text, keywords) != null;
    }

    /**
     * 构造规则命中记录。
     */
    private Hit hit(IntentResult.IntentType type, String keyword) {
        return new Hit(type, keyword);
    }

    /**
     * 将命中记录转为 IntentResult。
     */
    private IntentResult toResult(Hit hit) {
        IntentResult result = new IntentResult();
        result.setIntent(hit.type.name());
        result.setConfidence(RULE_CONFIDENCE);
        result.setReason("规则命中: " + hit.keyword);
        return result;
    }

    /**
     * 单次规则命中内部结构。
     */
    private record Hit(IntentResult.IntentType type, String keyword) {
    }
}
