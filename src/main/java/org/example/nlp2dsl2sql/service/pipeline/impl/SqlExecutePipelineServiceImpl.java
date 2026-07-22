package org.example.nlp2dsl2sql.service.pipeline.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.service.pipeline.ISqlExecutePipelineService;
import org.example.nlp2dsl2sql.tools.SqlExecuteTool;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * SQL 安全执行 Pipeline Service 实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlExecutePipelineServiceImpl implements ISqlExecutePipelineService {

    private final SqlExecuteTool sqlExecuteTool;

    /**
     * 安全执行 SQL。
     *
     * @param sql    参数化 SQL
     * @param params 参数列表
     * @return 查询结果
     */
    @Override
    public List<Map<String, Object>> execute(String sql, List<Object> params) {
        log.info("━━━ [Pipeline] EXECUTE 开始 ━━━");
        List<Map<String, Object>> result = sqlExecuteTool.executeSql(sql, params);
        log.info("━━━ [Pipeline] EXECUTE 完成: rows={} ━━━", result.size());
        return result;
    }
}
