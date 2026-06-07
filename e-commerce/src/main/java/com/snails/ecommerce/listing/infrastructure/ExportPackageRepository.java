package com.snails.ecommerce.listing.infrastructure;

import com.snails.ecommerce.listing.domain.ExportPackage;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 导出交付包仓储。
 */
public interface ExportPackageRepository extends JpaRepository<ExportPackage, String> {

    /**
     * 按创建时间和导出包 ID 倒序查询指定任务的导出历史。
     */
    List<ExportPackage> findByTaskIdOrderByCreatedAtDescExportPackageIdDesc(String taskId);

    /**
     * 按创建时间和导出包 ID 升序查询指定状态的导出记录。
     */
    List<ExportPackage> findByStatusOrderByCreatedAtAscExportPackageIdAsc(String status, Pageable pageable);

    /**
     * 按创建时间和导出包 ID 升序查询指定状态下已经超过时间阈值的导出记录。
     */
    List<ExportPackage> findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAscExportPackageIdAsc(
            String status,
            LocalDateTime updatedBefore);

    /**
     * 条件更新导出记录状态，用于 worker 原子领取待处理记录。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ExportPackage exportPackage
               set exportPackage.status = :targetStatus,
                   exportPackage.startedAt = :startedAt,
                   exportPackage.updatedAt = :updatedAt,
                   exportPackage.failureReason = null
             where exportPackage.exportPackageId = :exportPackageId
               and exportPackage.status = :expectedStatus
            """)
    int updateStatusWhenCurrentStatus(
            @Param("exportPackageId") String exportPackageId,
            @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("updatedAt") LocalDateTime updatedAt);
}
