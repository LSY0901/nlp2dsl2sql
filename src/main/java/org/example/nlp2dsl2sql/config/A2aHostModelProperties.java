package org.example.nlp2dsl2sql.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A2A Host 模型路由配置（按问题复杂度选择快/强模型）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "a2a.host.model")
public class A2aHostModelProperties {

    /** 未命中复杂度规则时的默认档位 key，默认 strong */
    private String defaultTier = "strong";

    /** 档位 key → 模型连接信息（fast / strong 等，可扩展） */
    private Map<String, ModelSpec> tiers = new LinkedHashMap<>();

    /** 复杂度规则 */
    private Complexity complexity = new Complexity();

    /**
     * 单个档位的模型连接信息。
     */
    @Data
    public static class ModelSpec {
        /** 模型名，如 deepseek-v4-flash / deepseek-reasoner */
        private String modelName;
        /** API Key；缺省时由框架默认模型兜底 */
        private String apiKey;
        /** 服务基地址；缺省时由框架默认模型兜底 */
        private String baseUrl;
    }

    /**
     * 复杂度判定规则（独立于意图分类）。
     */
    @Data
    public static class Complexity {
        /** 简单问题关键词，命中即用快模型 */
        private List<String> simpleKeywords = List.of(
                "平均分", "最高分", "最低分", "是多少", "有多少", "数量", "总数");

        /** 复杂问题关键词，命中即用强模型 */
        private List<String> complexKeywords = List.of(
                "对比", "比较", "分别", "各年级", "各个", "同比", "环比",
                "明细", "列出", "以及", "并且", "同时");

        /** 问题字符数超过该阈值视为复杂 */
        private int lengthThreshold = 60;
    }
}
