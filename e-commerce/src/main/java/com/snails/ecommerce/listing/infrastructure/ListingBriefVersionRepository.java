package com.snails.ecommerce.listing.infrastructure;

import com.snails.ecommerce.listing.domain.ListingBriefVersion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Listing Brief 版本仓储。
 */
public interface ListingBriefVersionRepository extends JpaRepository<ListingBriefVersion, String> {

    /**
     * 查询指定任务最新创建的 Brief 版本。
     */
    Optional<ListingBriefVersion> findTopByTaskIdOrderByCreatedAtDesc(String taskId);
}
