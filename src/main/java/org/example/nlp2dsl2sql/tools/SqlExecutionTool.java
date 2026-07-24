package org.example.nlp2dsl2sql.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 多 Agent 工具：安全 SQL 执行（仅 SELECT + 关键字黑名单 + 参数绑定）。
 * <p>
 * 对应原 Workflow Stage 7b，封装 {@link SqlExecuteTool} 的安全执行逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlExecutionTool {

    private final SqlExecuteTool sqlExecuteTool;

    /**
     * 安全执行 SQL 查询。
     *
     * @param sql    参数化 SQL
     * @param params 参数列表
     * @return 查询结果
     */
    public List<Map<String, Object>> execute(String sql, List<Object> params) {
        log.info("━━━ [Multi-Agent] SqlExecutionTool 执行 SQL ━━━");
        List<Map<String, Object>> result = sqlExecuteTool.executeSql(sql, params);
        log.info("━━━ [Multi-Agent] SqlExecutionTool 完成: rows={} ━━━", result.size());
        return result;
    }
}
