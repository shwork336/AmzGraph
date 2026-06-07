package com.snails.ecommerce.listing.infrastructure;

import com.snails.ecommerce.listing.domain.ImageVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Listing 图片版本仓储。
 *
 * <p>图片版本按任务聚合查询，排序规则服务于运营侧版本对比和最新版本判定。</p>
 */
public interface ImageVersionRepository extends JpaRepository<ImageVersion, String> {

    /**
     * 查询指定任务最新创建的图片版本。
     */
    Optional<ImageVersion> findTopByTaskIdOrderByCreatedAtDescVersionIdDesc(String taskId);

    /**
     * 按创建时间和版本 ID 倒序查询指定任务的图片版本历史。
     */
    List<ImageVersion> findByTaskIdOrderByCreatedAtDescVersionIdDesc(String taskId);
}
