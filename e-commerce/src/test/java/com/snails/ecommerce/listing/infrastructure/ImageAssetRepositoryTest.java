package com.snails.ecommerce.listing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.snails.ecommerce.listing.domain.ImageAsset;
import com.snails.ecommerce.template.domain.ImageAssetType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 图片资产持久化测试。
 *
 * <p>验证图片资产保存和图片版本维度的组内稳定排序。</p>
 */
@SpringBootTest
class ImageAssetRepositoryTest {

    @Autowired
    private ImageAssetRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void savesImageAsset() {
        ImageAsset asset = baseAsset("asset_001", "image_001", ImageAssetType.MAIN_IMAGE, 1);
        asset.setGeneratedImageUrl("generated-images/image_001/MAIN_IMAGE.png");
        asset.setSizeProfile("MAIN_IMAGE");
        asset.setTargetWidth(2000);
        asset.setTargetHeight(2000);
        asset.setComplianceStatus("PASS");
        asset.setComplianceMethodsJson("[\"PLACEHOLDER_RULE_CHECK\"]");
        asset.setComplianceIssuesJson("[]");

        repository.save(asset);

        ImageAsset saved = repository.findById("asset_001").orElseThrow();
        assertThat(saved.getImageVersionId()).isEqualTo("image_001");
        assertThat(saved.getType()).isEqualTo(ImageAssetType.MAIN_IMAGE);
        assertThat(saved.getPrompt()).contains("MAIN_IMAGE");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void listsAssetsBySortOrderAndAssetIdAscending() {
        ImageAsset first = baseAsset("asset_001", "image_001", ImageAssetType.MAIN_IMAGE, 1);
        repository.save(first);

        ImageAsset third = baseAsset("asset_003", "image_001", ImageAssetType.LIFESTYLE, 3);
        repository.save(third);

        ImageAsset sameOrderHigherId = baseAsset("asset_002", "image_001", ImageAssetType.INFOGRAPHIC, 3);
        repository.save(sameOrderHigherId);

        ImageAsset otherVersion = baseAsset("asset_other", "image_other", ImageAssetType.MAIN_IMAGE, 0);
        repository.save(otherVersion);

        List<ImageAsset> assets =
                repository.findByImageVersionIdOrderBySortOrderAscAssetIdAsc("image_001");

        assertThat(assets)
                .extracting(ImageAsset::getAssetId)
                .containsExactly("asset_001", "asset_002", "asset_003");
    }

    private ImageAsset baseAsset(
            String assetId,
            String imageVersionId,
            ImageAssetType type,
            int sortOrder) {
        ImageAsset asset = new ImageAsset();
        asset.setAssetId(assetId);
        asset.setImageVersionId(imageVersionId);
        asset.setType(type);
        asset.setPrompt("Generate " + type.name() + " image");
        asset.setRewrittenPrompt("Placeholder rewritten prompt for " + type.name());
        asset.setGeneratedImageUrl("generated-images/" + imageVersionId + "/" + type.name() + ".png");
        asset.setSizeProfile("STANDARD_LISTING");
        asset.setTargetWidth(2000);
        asset.setTargetHeight(2000);
        asset.setComplianceStatus("PASS");
        asset.setComplianceMethodsJson("[\"PLACEHOLDER_RULE_CHECK\"]");
        asset.setComplianceIssuesJson("[]");
        asset.setSortOrder(sortOrder);
        return asset;
    }
}
