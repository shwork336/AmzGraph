package com.snails.ecommerce.competitor.infrastructure;

import com.snails.ecommerce.competitor.domain.CompetitorSnapshot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 竞品快照仓储。
 */
public interface CompetitorSnapshotRepository extends JpaRepository<CompetitorSnapshot, String> {

    /**
     * 按采集时间和快照 ID 倒序查询任务的完整竞品快照历史。
     */
    List<CompetitorSnapshot> findByTaskIdOrderByCapturedAtDescSnapshotIdDesc(String taskId);
}
