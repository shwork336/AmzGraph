package com.snails.ecommerce.listing.infrastructure;

import com.snails.ecommerce.listing.domain.ImageAsset;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 图片资产仓储。
 */
public interface ImageAssetRepository extends JpaRepository<ImageAsset, String> {
}
