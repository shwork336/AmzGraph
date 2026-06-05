package com.snails.ecommerce.common.id;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
/**
 * 字符串 ID 生成器。
 *
 * <p>领域实体使用带前缀的 UUID 字符串，避免把数据库自增主键直接暴露给前端。</p>
 */
public class IdGenerator {

    /**
     * 生成形如 {@code task_xxx}、{@code brief_xxx} 的业务 ID。
     */
    public String generate(String prefix) {
        String safePrefix = StringUtils.hasText(prefix) ? prefix.trim() : "id";
        return safePrefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
