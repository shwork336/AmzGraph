package com.snails.ecommerce.listing.infrastructure;

import com.snails.ecommerce.listing.domain.ImageAsset;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 图片资产仓储。
 */
public interface ImageAssetRepository extends JpaRepository<ImageAsset, String> {

    /**
     * 按图片版本查询资产，并按组内排序和资产 ID 稳定排序。
     */
    List<ImageAsset> findByImageVersionIdOrderBySortOrderAscAssetIdAsc(String imageVersionId);
}
