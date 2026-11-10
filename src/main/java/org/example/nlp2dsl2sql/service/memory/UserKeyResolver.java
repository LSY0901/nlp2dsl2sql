package org.example.nlp2dsl2sql.service.memory;

import org.springframework.stereotype.Component;

/**
 * 记忆归属的 userId 解析器：传入 userId 优先，否则由 sessionId 派生。
 * <p>
 * 派生规则与 A2A Host 现有逻辑一致（"user_" + sessionId 前 8 位），
 * 收敛到一处避免两套派生结果对不上。演示环境前端不传 userId 时，
 * 5 种编排形态会派生出同一个 user key，画像自然汇聚。
 */
@Component
public class UserKeyResolver {

    /**
     * 解析记忆归属 userId。
     *
     * @param sessionId 会话 ID（派生用）
     * @param userId    显式 userId，可为 null
     * @return 归一化的 userId
     */
    public String resolve(String sessionId, String userId) {
        if (userId != null && !userId.isBlank()) {
            return userId.trim();
        }
        if (sessionId == null || sessionId.isBlank()) {
            return "user_anonymous";
        }
        String sid = sessionId.trim();
        return "user_" + (sid.length() > 8 ? sid.substring(0, 8) : sid);
    }
}
