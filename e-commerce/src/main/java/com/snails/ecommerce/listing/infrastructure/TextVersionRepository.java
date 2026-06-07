package com.snails.ecommerce.listing.infrastructure;

import com.snails.ecommerce.listing.domain.TextVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Listing 文案版本仓储。
 *
 * <p>文案版本按任务聚合查询，排序规则服务于运营侧版本对比和最新版本判定。</p>
 */
public interface TextVersionRepository extends JpaRepository<TextVersion, String> {

    /**
     * 查询指定任务最新创建的文案版本。
     */
    Optional<TextVersion> findTopByTaskIdOrderByCreatedAtDescVersionIdDesc(String taskId);

    /**
     * 按创建时间和版本 ID 倒序查询指定任务的文案版本历史。
     */
    List<TextVersion> findByTaskIdOrderByCreatedAtDescVersionIdDesc(String taskId);
}
