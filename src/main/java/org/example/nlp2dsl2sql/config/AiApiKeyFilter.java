package org.example.nlp2dsl2sql.config;

import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * /aiChat/* 极简 API-Key 网关：header {@code X-API-KEY} 与 {@code ai.api-key} 比对，
 * 对不上直接 401。key 为空则启动 fail-fast（防无保护上线）。
 * 预检 OPTIONS 与非 /aiChat 路径（如静态页、agent-card）放行。
 */
@Slf4j
@Component
public class AiApiKeyFilter extends OncePerRequestFilter {

    private final String apiKey;

    public AiApiKeyFilter(@Value("${ai.api-key:}") String apiKey) {
        String key = apiKey == null ? "" : apiKey.trim();
        if (key.isEmpty()) {
            throw new IllegalStateException(
                    "ai.api-key must be configured (application-local.yaml 或 AI_API_KEY 环境变量)");
        }
        this.apiKey = key;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/aiChat/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String got = request.getHeader("X-API-KEY");
        if (apiKey.equals(got == null ? "" : got.trim())) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("━━━ [网关鉴权] 拒绝 {}{} ━━━", request.getMethod(), request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        JSONObject body = new JSONObject();
        body.put("ok", false);
        body.put("message", "unauthorized: 缺少或错误的 X-API-KEY");
        response.getWriter().write(body.toJSONString());
    }
}
