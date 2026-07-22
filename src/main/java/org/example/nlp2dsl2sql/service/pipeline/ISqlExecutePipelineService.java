package org.example.nlp2dsl2sql.service.pipeline;

import java.util.List;
import java.util.Map;

/**
 * SQL 安全执行 Pipeline Service。
 */
public interface ISqlExecutePipelineService {

    /**
     * 安全执行 SELECT SQL。
     *
     * @param sql    参数化 SQL
     * @param params 参数列表
     * @return 查询结果行
     */
    List<Map<String, Object>> execute(String sql, List<Object> params);
}
