package org.example.nlp2dsl2sql.a2a;

import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.spring.boot.openai.OpenAIProperties;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.config.A2aHostModelProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A2A Host 模型路由器：按问题复杂度选择档位模型。
 * <p>
 * 档位模型懒加载构建并缓存；档位未配置或构建失败时回退框架默认模型。
 */
@Slf4j
@Component
public class A2aHostModelRouter {

    private final A2aHostModelProperties properties;
    private final QuestionComplexityClassifier classifier;
    private final OpenAIProperties defaultOpenAIProperties;
    private final OpenAIChatModel defaultModel;
    private final Map<String, OpenAIChatModel> tierCache = new ConcurrentHashMap<>();

    /**
     * @param properties             档位与复杂度配置
     * @param classifier             复杂度分类器
     * @param defaultOpenAIProperties 全局 OpenAI 连接配置（兜底 apiKey/baseUrl）
     * @param defaultModel            框架默认模型（兜底）
     */
    public A2aHostModelRouter(
            A2aHostModelProperties properties,
            QuestionComplexityClassifier classifier,
            OpenAIProperties defaultOpenAIProperties,
            @Qualifier("openAIChatModel") OpenAIChatModel defaultModel) {
        this.properties = properties;
        this.classifier = classifier;
        this.defaultOpenAIProperties = defaultOpenAIProperties;
        this.defaultModel = defaultModel;
    }

    /**
     * 按问题复杂度解析出模型。
     *
     * @param question 用户问题
     * @return 选中的模型；无配置或构建失败时返回默认模型
     */
    public OpenAIChatModel resolve(String question) {
        String tier = classifier.classify(question);
        OpenAIChatModel model = resolveTier(tier);
        log.info("[ModelRouter] question={}, tier={}, model={}",
                abbreviate(question), tier,
                model == null ? "default" : model.getModelName());
        return model;
    }

    /**
     * 按档位 key 取模型，未命中档位回退默认模型。
     *
     * @param tier 档位 key
     * @return 模型
     */
    public OpenAIChatModel resolveTier(String tier) {
        if (tier == null || tier.isBlank()) {
            return defaultModel;
        }
        if (properties.getTiers() == null
                || !properties.getTiers().containsKey(tier)) {
            log.warn("[ModelRouter] 未配置档位 {}，回退默认模型", tier);
            return defaultModel;
        }
        return tierCache.computeIfAbsent(tier, this::buildTierModel);
    }

    /**
     * 构建单个档位模型；配置缺失或构建失败回退默认模型。
     *
     * @param tier 档位 key
     * @return 档位模型或默认模型
     */
    private OpenAIChatModel buildTierModel(String tier) {
        A2aHostModelProperties.ModelSpec spec = properties.getTiers().get(tier);
        if (spec == null || spec.getModelName() == null
                || spec.getModelName().isBlank()) {
            log.warn("[ModelRouter] 档位 {} 缺少 modelName，回退默认模型", tier);
            return defaultModel;
        }
        try {
            OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                    .modelName(spec.getModelName())
                    .apiKey(resolveApiKey(spec))
                    .baseUrl(resolveBaseUrl(spec));
            OpenAIChatModel model = builder.build();
            log.info("[ModelRouter] 构建档位模型 tier={}, modelName={}",
                    tier, spec.getModelName());
            return model;
        } catch (Exception e) {
            log.warn("[ModelRouter] 构建档位 {} 失败: {}，回退默认模型",
                    tier, e.getMessage());
            return defaultModel;
        }
    }

    /**
     * 档位 apiKey，缺省用全局配置。
     */
    private String resolveApiKey(A2aHostModelProperties.ModelSpec spec) {
        if (spec.getApiKey() != null && !spec.getApiKey().isBlank()) {
            return spec.getApiKey();
        }
        return defaultOpenAIProperties.getApiKey();
    }

    /**
     * 档位 baseUrl，缺省用全局配置。
     */
    private String resolveBaseUrl(A2aHostModelProperties.ModelSpec spec) {
        if (spec.getBaseUrl() != null && !spec.getBaseUrl().isBlank()) {
            return spec.getBaseUrl();
        }
        return defaultOpenAIProperties.getBaseUrl();
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
