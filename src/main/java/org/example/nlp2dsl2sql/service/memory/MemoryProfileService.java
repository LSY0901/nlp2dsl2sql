package org.example.nlp2dsl2sql.service.memory;

import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.mapper.memory.MemoryUserProfileMapper;
import org.example.nlp2dsl2sql.models.entity.memory.MemoryUserProfile;
import org.springframework.stereotype.Component;

/**
 * 用户画像读取服务：长期记忆读写一律 PG（短期才走 Redis）。
 * <p>
 * 无画像或存储异常返回 null（跳过注入，不阻断改写）。
 */
@Slf4j
@Component
public class MemoryProfileService {

    private final MemoryUserProfileMapper profileMapper;

    /**
     * @param profileMapper 画像 Mapper
     */
    public MemoryProfileService(MemoryUserProfileMapper profileMapper) {
        this.profileMapper = profileMapper;
    }

    /**
     * 读取画像 JSON（PG 直读）。
     *
     * @param userId 用户 ID
     * @return 画像 JSON 字符串；无画像或存储异常返回 null
     */
    public String getProfile(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            MemoryUserProfile profile = profileMapper.selectById(userId.trim());
            if (profile == null || profile.getProfileJson() == null) {
                return null;
            }
            return profile.getProfileJson();
        } catch (Exception e) {
            log.warn("[MemoryProfile] 画像读取 PG 失败: userId={}, error={}",
                    userId, e.getMessage());
            return null;
        }
    }
}
