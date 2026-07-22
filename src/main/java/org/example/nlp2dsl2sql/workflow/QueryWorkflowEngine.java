package org.example.nlp2dsl2sql.workflow;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.models.entity.ReviewResult;
import org.example.nlp2dsl2sql.planner.IQueryPlanner;
import org.example.nlp2dsl2sql.planner.model.FailureAction;
import org.example.nlp2dsl2sql.planner.model.PlanStep;
import org.example.nlp2dsl2sql.planner.model.QueryPlan;
import org.example.nlp2dsl2sql.planner.model.StepType;
import org.example.nlp2dsl2sql.semanticdsl.model.DslCandidate;
import org.example.nlp2dsl2sql.semanticdsl.model.EnrichedQueryDSL;
import org.example.nlp2dsl2sql.semanticdsl.model.IntentResult;
import org.example.nlp2dsl2sql.semanticdsl.model.SemanticQueryDSL;
import org.example.nlp2dsl2sql.semanticdsl.translator.DslTranslator;
import org.example.nlp2dsl2sql.semanticdsl.validator.SemanticDslValidator;
import org.example.nlp2dsl2sql.service.pipeline.IAnswerPipelineService;
import org.example.nlp2dsl2sql.service.pipeline.IDslGeneratePipelineService;
import org.example.nlp2dsl2sql.service.pipeline.IEnrichmentPipelineService;
import org.example.nlp2dsl2sql.service.pipeline.IRetrievalPipelineService;
import org.example.nlp2dsl2sql.service.pipeline.IReviewPipelineService;
import org.example.nlp2dsl2sql.service.pipeline.ISqlExecutePipelineService;
import org.example.nlp2dsl2sql.service.pipeline.ITranslationPipelineService;
import org.example.nlp2dsl2sql.service.pipeline.IValidationPipelineService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 查询 Workflow 引擎实现。
 * <p>
 * 负责计划校验、步骤调度、失败重试/重规划、SSE 组装。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryWorkflowEngine implements IQueryWorkflowEngine {

    private static final Set<StepType> REQUIRED_STEPS = EnumSet.of(
            StepType.RETRIEVE,
            StepType.GENERATE_DSL,
            StepType.VALIDATE,
            StepType.ENRICH,
            StepType.TRANSLATE,
            StepType.REVIEW,
            StepType.EXECUTE,
            StepType.ANSWER
    );

    private final IQueryPlanner queryPlanner;
    private final IRetrievalPipelineService retrievalService;
    private final IDslGeneratePipelineService dslGenerateService;
    private final IValidationPipelineService validationService;
    private final IEnrichmentPipelineService enrichmentService;
    private final ITranslationPipelineService translationService;
    private final IReviewPipelineService reviewService;
    private final ISqlExecutePipelineService sqlExecuteService;
    private final IAnswerPipelineService answerService;

    /**
     * 运行完整管线并返回 SSE 流。
     *
     * @param question 用户问题
     * @return SSE 文本流
     */
    @Override
    public Flux<String> run(String question) {
        if (question == null || question.isBlank()) {
            return Flux.just("错误: 问题不能为空");
        }
        String trimmed = question.trim();
        return Flux.defer(() -> {
            try {
                return executeWorkflow(trimmed);
            } catch (WorkflowException e) {
                log.warn("Workflow 业务失败: {}", e.getMessage());
                return Flux.just("错误: " + e.getMessage());
            } catch (Exception e) {
                log.error("Workflow 执行失败", e);
                return Flux.just("错误: 系统处理失败，请稍后重试");
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 执行规划 → 调度 →（可选）重规划循环。
     *
     * @param question 用户问题
     * @return SSE 流
     */
    private Flux<String> executeWorkflow(String question) {
        log.info("━━━━━━━ Planner-Workflow 启动 ━━━━━━━");
        WorkflowContext ctx = new WorkflowContext();
        ctx.setQuestion(question);

        QueryPlan plan = queryPlanner.plan(question);
        ctx.setPlan(plan);

        while (true) {
            try {
                validatePlan(plan);
            } catch (WorkflowException ve) {
                // 计划非法：计入失败并尝试重规划
                ctx.setFailedStep(null);
                ctx.setLastError(ve.getMessage());
                plan = doReplan(ctx);
                continue;
            }

            IntentResult.IntentType intent =
                    IntentResult.parseIntentType(plan.getIntent());

            if (intent == IntentResult.IntentType.NON_BUSINESS) {
                String reason = plan.getReason() == null
                        ? "非业务问题，无法处理"
                        : plan.getReason();
                return Flux.just("[计划] NON_BUSINESS\n" + reason);
            }

            ctx.addProgress("[计划] " + intent.name() + "\n");
            StepLoopResult loopResult = runSteps(ctx, intent);

            if (loopResult == StepLoopResult.NEED_REPLAN) {
                plan = doReplan(ctx);
                continue;
            }
            return buildOutputFlux(ctx);
        }
    }

    /**
     * 触发重规划并清空半成品状态。
     *
     * @param ctx 上下文
     * @return 新计划
     */
    private QueryPlan doReplan(WorkflowContext ctx) {
        QueryPlan oldPlan = ctx.getPlan();
        int max = oldPlan.getMaxReplan() <= 0 ? 2 : oldPlan.getMaxReplan();
        if (ctx.getReplanCount() >= max) {
            throw new WorkflowException(
                    "重规划次数已达上限(" + max + "): " + ctx.getLastError());
        }
        ctx.setReplanCount(ctx.getReplanCount() + 1);
        QueryPlan newPlan = queryPlanner.replan(
                ctx.getQuestion(),
                oldPlan,
                ctx.getFailedStep(),
                ctx.getLastError(),
                buildContextSummary(ctx));
        boolean clearCandidate = containsStep(newPlan, StepType.RETRIEVE);
        ctx.clearDerivedState(clearCandidate);
        ctx.setPlan(newPlan);
        ctx.addProgress("[重规划] 第 " + ctx.getReplanCount() + " 次\n");
        return newPlan;
    }

    /**
     * 按计划顺序执行步骤。
     *
     * @param ctx    上下文
     * @param intent 意图
     * @return 循环结果
     */
    private StepLoopResult runSteps(WorkflowContext ctx,
                                    IntentResult.IntentType intent) {
        List<PlanStep> steps = ctx.getPlan().getSteps();
        for (PlanStep step : steps) {
            if (step.getType() == null) {
                throw new WorkflowException("计划含未知步骤类型");
            }
            // REVIEW 不可真正跳过
            boolean skip = step.isSkip() && step.getType() != StepType.REVIEW;
            if (skip) {
                ctx.addProgress("[步骤] " + step.getType() + " 跳过\n");
                continue;
            }
            if (step.getType() == StepType.ANSWER) {
                ctx.addProgress("[步骤] ANSWER 开始\n");
                return StepLoopResult.COMPLETED;
            }

            StepLoopResult result = executeStepWithRetry(ctx, step, intent);
            if (result == StepLoopResult.NEED_REPLAN) {
                return StepLoopResult.NEED_REPLAN;
            }
        }
        // 计划未含 ANSWER（校验应已拦截），兜底
        return StepLoopResult.COMPLETED;
    }

    /**
     * 单步执行（含本步 retry）。
     *
     * @param ctx    上下文
     * @param step   步骤
     * @param intent 意图
     * @return 结果
     */
    private StepLoopResult executeStepWithRetry(WorkflowContext ctx,
                                                PlanStep step,
                                                IntentResult.IntentType intent) {
        int maxRetry = Math.max(0, step.getRetry());
        int attempt = 0;
        while (true) {
            try {
                ctx.addProgress("[步骤] " + step.getType() + " 开始\n");
                dispatchStep(ctx, step.getType(), intent);
                ctx.addProgress("[步骤] " + step.getType() + " 完成\n");
                return StepLoopResult.CONTINUE;
            } catch (Exception e) {
                String err = e.getMessage() == null ? e.getClass().getSimpleName()
                        : e.getMessage();
                log.warn("[Workflow] 步骤失败: type={}, err={}",
                        step.getType(), err);
                ctx.setFailedStep(step.getType());
                ctx.setLastError(err);

                if (attempt < maxRetry) {
                    attempt++;
                    log.info("[Workflow] 步骤重试 {}/{}: {}",
                            attempt, maxRetry, step.getType());
                    continue;
                }
                return handleFailure(ctx, step, err);
            }
        }
    }

    /**
     * 按 onFailure 处理步骤失败。
     *
     * @param ctx  上下文
     * @param step 步骤
     * @param err  错误
     * @return 循环结果
     */
    private StepLoopResult handleFailure(WorkflowContext ctx,
                                         PlanStep step,
                                         String err) {
        FailureAction action = step.getOnFailure() == null
                ? FailureAction.ABORT
                : step.getOnFailure();
        // 关键步骤不允许 SKIP
        if (action == FailureAction.SKIP
                && REQUIRED_STEPS.contains(step.getType())) {
            action = FailureAction.ABORT;
        }
        switch (action) {
            case RETRY:
                // retry 已在上层耗尽
                throw new WorkflowException("步骤失败且重试耗尽: "
                        + step.getType() + " - " + err);
            case SKIP:
                ctx.addProgress("[步骤] " + step.getType() + " 失败后跳过\n");
                return StepLoopResult.CONTINUE;
            case REPLAN:
                return StepLoopResult.NEED_REPLAN;
            case ABORT:
            default:
                throw new WorkflowException("步骤失败: "
                        + step.getType() + " - " + err);
        }
    }

    /**
     * 分发到具体 Pipeline Service。
     *
     * @param ctx    上下文
     * @param type   步骤类型
     * @param intent 意图
     */
    private void dispatchStep(WorkflowContext ctx,
                              StepType type,
                              IntentResult.IntentType intent) {
        switch (type) {
            case RETRIEVE -> doRetrieve(ctx);
            case GENERATE_DSL -> doGenerateDsl(ctx, intent);
            case VALIDATE -> doValidate(ctx, intent);
            case ENRICH -> doEnrich(ctx);
            case TRANSLATE -> doTranslate(ctx);
            case REVIEW -> doReview(ctx);
            case EXECUTE -> doExecute(ctx);
            case ANSWER -> {
                // 由外层流式处理
            }
            default -> throw new WorkflowException("不支持的步骤: " + type);
        }
    }

    /**
     * 执行检索步骤。
     *
     * @param ctx 上下文
     */
    private void doRetrieve(WorkflowContext ctx) {
        DslCandidate candidate = retrievalService.retrieve(ctx.getQuestion());
        int metrics = sizeOf(candidate.getMetrics());
        int entities = sizeOf(candidate.getEntities());
        if (metrics == 0 && entities == 0) {
            throw new WorkflowException("未检索到相关指标/实体，请换一种问法");
        }
        ctx.setCandidate(candidate);
    }

    /**
     * 执行 DSL 生成步骤。
     *
     * @param ctx    上下文
     * @param intent 意图
     */
    private void doGenerateDsl(WorkflowContext ctx,
                               IntentResult.IntentType intent) {
        requireCandidate(ctx);
        SemanticQueryDSL dsl = dslGenerateService.generate(
                ctx.getQuestion(),
                ctx.getCandidate(),
                intent,
                ctx.getPlan().getGoal());
        ctx.setSemanticDSL(dsl);
    }

    /**
     * 执行校验步骤。
     *
     * @param ctx    上下文
     * @param intent 意图
     */
    private void doValidate(WorkflowContext ctx,
                            IntentResult.IntentType intent) {
        if (ctx.getSemanticDSL() == null) {
            throw new WorkflowException("缺少 semanticDSL，无法校验");
        }
        SemanticDslValidator.ValidationResult result =
                validationService.validate(ctx.getSemanticDSL(), intent);
        if (!result.valid()) {
            throw new WorkflowException("DSL校验失败: " + result.errors());
        }
    }

    /**
     * 执行富化步骤。
     *
     * @param ctx 上下文
     */
    private void doEnrich(WorkflowContext ctx) {
        requireCandidate(ctx);
        if (ctx.getSemanticDSL() == null) {
            throw new WorkflowException("缺少 semanticDSL，无法富化");
        }
        EnrichedQueryDSL enriched = enrichmentService.enrich(
                ctx.getSemanticDSL(), ctx.getCandidate());
        if (enriched.getMainPhysicalTable() == null
                || enriched.getSelectColumns() == null
                || enriched.getSelectColumns().isEmpty()) {
            throw new WorkflowException("DSL富化结果不完整，无法生成SQL");
        }
        ctx.setEnrichedDSL(enriched);
    }

    /**
     * 执行 SQL 翻译步骤。
     *
     * @param ctx 上下文
     */
    private void doTranslate(WorkflowContext ctx) {
        if (ctx.getEnrichedDSL() == null) {
            throw new WorkflowException("缺少 enrichedDSL，无法翻译");
        }
        DslTranslator.TranslatedSql translated =
                translationService.translate(ctx.getEnrichedDSL());
        ctx.setSql(translated.sql());
        ctx.setParams(translated.parameters());
    }

    /**
     * 执行 SQL 审查步骤。
     *
     * @param ctx 上下文
     */
    private void doReview(WorkflowContext ctx) {
        if (ctx.getSql() == null || ctx.getEnrichedDSL() == null) {
            throw new WorkflowException("缺少 SQL/enrichedDSL，无法审查");
        }
        ReviewResult review = reviewService.review(
                ctx.getSql(), ctx.getEnrichedDSL(), ctx.getQuestion());
        if (!Boolean.TRUE.equals(review.getResult())) {
            String reason = review.getReason() == null
                    ? "未知原因" : review.getReason();
            ctx.setReviewPassed(false);
            throw new WorkflowException("SQL审查未通过: " + reason);
        }
        ctx.setReviewPassed(true);
    }

    /**
     * 执行 SQL 查询步骤（强制审查门禁）。
     *
     * @param ctx 上下文
     */
    private void doExecute(WorkflowContext ctx) {
        if (!ctx.isReviewPassed()) {
            throw new WorkflowException("EXECUTE 前必须完成 REVIEW 且通过");
        }
        if (ctx.getSql() == null) {
            throw new WorkflowException("缺少 SQL，无法执行");
        }
        ctx.setQueryResult(sqlExecuteService.execute(
                ctx.getSql(), ctx.getParams()));
    }

    /**
     * 组装最终 SSE 输出。
     *
     * @param ctx 上下文
     * @return Flux
     */
    private Flux<String> buildOutputFlux(WorkflowContext ctx) {
        String intentBlock = "";
        if (ctx.getPlan() != null && ctx.getPlan().getIntent() != null) {
            intentBlock = "意图：" + ctx.getPlan().getIntent() + "\n\n";
        }
        String sqlBlock = ctx.getSql() == null
                ? ""
                : "SQL：\n" + ctx.getSql() + "\n\n结论：\n";

        Flux<String> progress = Flux.fromIterable(ctx.getProgressMessages());
        Flux<String> answer = answerService.streamAnswer(
                ctx.getQuestion(), ctx.getSql(), ctx.getQueryResult());

        return Flux.concat(
                progress,
                Flux.just(intentBlock),
                Flux.just(sqlBlock),
                answer
        );
    }

    /**
     * 校验计划合法性与关键步骤完整性。
     *
     * @param plan 计划
     */
    private void validatePlan(QueryPlan plan) {
        if (plan == null) {
            throw new WorkflowException("查询计划为空");
        }
        IntentResult.IntentType intent =
                IntentResult.parseIntentType(plan.getIntent());
        if (intent == IntentResult.IntentType.NON_BUSINESS) {
            return;
        }
        if (plan.getSteps() == null || plan.getSteps().isEmpty()) {
            throw new WorkflowException("业务计划 steps 不能为空");
        }
        Set<StepType> types = plan.getSteps().stream()
                .map(PlanStep::getType)
                .filter(t -> t != null)
                .collect(Collectors.toCollection(
                        () -> EnumSet.noneOf(StepType.class)));
        if (!types.containsAll(REQUIRED_STEPS)) {
            Set<StepType> missing = EnumSet.copyOf(REQUIRED_STEPS);
            missing.removeAll(types);
            throw new WorkflowException("计划缺少关键步骤: " + missing);
        }
        // 强制 REVIEW 不可 skip
        for (PlanStep step : plan.getSteps()) {
            if (step.getType() == StepType.REVIEW) {
                step.setSkip(false);
                if (step.getOnFailure() == FailureAction.SKIP) {
                    step.setOnFailure(FailureAction.REPLAN);
                }
            }
        }
    }

    /**
     * 构建重规划用上下文摘要。
     *
     * @param ctx 上下文
     * @return 摘要文本
     */
    private String buildContextSummary(WorkflowContext ctx) {
        StringBuilder sb = new StringBuilder();
        if (ctx.getSemanticDSL() != null) {
            sb.append("semanticDSL=").append(JSON.toJSONString(ctx.getSemanticDSL()));
        }
        if (ctx.getSql() != null) {
            sb.append("; sql=").append(ctx.getSql());
        }
        return sb.toString();
    }

    /**
     * 判断计划是否包含某步骤。
     *
     * @param plan 计划
     * @param type 步骤类型
     * @return 是否包含
     */
    private boolean containsStep(QueryPlan plan, StepType type) {
        if (plan.getSteps() == null) {
            return false;
        }
        return plan.getSteps().stream().anyMatch(s -> s.getType() == type);
    }

    /**
     * 要求 candidate 已存在。
     *
     * @param ctx 上下文
     */
    private void requireCandidate(WorkflowContext ctx) {
        if (ctx.getCandidate() == null) {
            throw new WorkflowException("缺少检索候选集，请先执行 RETRIEVE");
        }
    }

    /**
     * 安全获取列表大小。
     *
     * @param list 列表
     * @return 大小
     */
    private int sizeOf(List<?> list) {
        return list == null ? 0 : list.size();
    }

    /**
     * 步骤循环结果。
     */
    private enum StepLoopResult {
        /** 继续下一步 */
        CONTINUE,
        /** 需要重规划 */
        NEED_REPLAN,
        /** 步骤执行完毕，进入回答 */
        COMPLETED
    }
}
