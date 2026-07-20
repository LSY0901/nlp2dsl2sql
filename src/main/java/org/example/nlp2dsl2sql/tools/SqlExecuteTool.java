package org.example.nlp2dsl2sql.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * SQL 执行工具：仅允许 SELECT，支持 JDBC 参数绑定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlExecuteTool {

    private final JdbcTemplate jdbcTemplate;

    private static final List<String> FORBIDDEN_KEYWORDS = List.of(
            "drop", "truncate", "alter", "create", "grant", "revoke",
            "insert", "update", "delete", "merge",
            "exec", "execute", "pg_sleep", "pg_terminate_backend",
            "copy", "lo_import", "lo_export"
    );

    public List<Map<String, Object>> executeSql(String sql) {
        return executeSql(sql, null);
    }

    public List<Map<String, Object>> executeSql(String sql, List<Object> params) {
        String trimmed = sql == null ? "" : sql.trim();
        String lower = trimmed.toLowerCase();

        if (!lower.startsWith("select")) {
            throw new IllegalArgumentException("只允许SELECT语句");
        }
        if (trimmed.contains(";")) {
            throw new IllegalArgumentException("不允许包含分号的多语句SQL");
        }

        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (lower.matches(".*\\b" + keyword + "\\b.*")) {
                throw new IllegalArgumentException("SQL包含禁止的关键字: " + keyword);
            }
        }

        try {
            if (params != null && !params.isEmpty()) {
                return jdbcTemplate.queryForList(trimmed, params.toArray());
            }
            return jdbcTemplate.queryForList(trimmed);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("SQL执行失败: {}", e.getMessage());
            throw new RuntimeException("SQL执行失败: " + e.getMessage(), e);
        }
    }
}
