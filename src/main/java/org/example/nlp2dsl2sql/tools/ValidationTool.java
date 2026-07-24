package org.example.nlp2dsl2sql.tools;

import org.example.nlp2dsl2sql.models.dto.dsl.IntentResult;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticQueryDSL;
import org.example.nlp2dsl2sql.semanticdsl.validator.SemanticDslValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 多 Agent 工具：DSL 校验。
 * <p>
 * 对应原 Workflow Stage 4，封装 {@link SemanticDslValidator} 的确定性逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidationTool {

    private final SemanticDslValidator dslValidator;

    /**
     * 校验 DSL 的合法性与兼容性。
     *
     * @param dsl    语义 DSL
     * @param intent 意图类型
     * @return 校验结果
     */
    public SemanticDslValidator.ValidationResult validate(SemanticQueryDSL dsl,
                                                           IntentResult.IntentType intent) {
        log.info("━━━ [Multi-Agent] ValidationTool 启动 ━━━");
        SemanticDslValidator.ValidationResult result = dslValidator.validate(dsl, intent);
        log.info("━━━ [Multi-Agent] ValidationTool 完成: valid={}, errors={} ━━━",
                result.valid(), result.errors());
        return result;
    }
}
