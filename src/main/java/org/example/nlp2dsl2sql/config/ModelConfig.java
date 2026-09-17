package org.example.nlp2dsl2sql.config;

import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.spring.boot.openai.OpenAIProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 默认模型 Bean：starter 的自动配置只在容器中尚无 {@code Model} 时才建模，
 * 而 {@code DynamicRoutingChatModel}（@Component implements Model）恰好让它退避，
 * 导致全场注入的 {@code OpenAIChatModel} 无处可来。此处由仓库自建兜底，
 * starter 退避即无关紧要。
 */
@Configuration
public class ModelConfig {

    @Bean(name = "openAIChatModel")
    @Primary
    public OpenAIChatModel openAIChatModel(OpenAIProperties props) {
        String apiKey = trimToNull(props.getApiKey());
        if (apiKey == null) {
            throw new IllegalStateException(
                    "agentscope.openai.api-key must be configured");
        }
        String modelName = trimToNull(props.getModelName());
        if (modelName == null) {
            throw new IllegalStateException(
                    "agentscope.openai.model-name must be configured");
        }
        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .stream(props.isStream());
        if (trimToNull(props.getBaseUrl()) != null) {
            builder.baseUrl(props.getBaseUrl().trim());
        }
        if (trimToNull(props.getEndpointPath()) != null) {
            builder.endpointPath(props.getEndpointPath().trim());
        }
        return builder.build();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
