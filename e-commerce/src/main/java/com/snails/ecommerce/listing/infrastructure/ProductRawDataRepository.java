package com.snails.ecommerce.listing.infrastructure;

import com.snails.ecommerce.listing.domain.ProductRawData;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 产品原始资料仓储。
 */
public interface ProductRawDataRepository extends JpaRepository<ProductRawData, String> {

    /**
     * 按任务 ID 查询产品资料解析结果。
     */
    Optional<ProductRawData> findByTaskId(String taskId);
}
