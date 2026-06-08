package com.snails.ecommerce.listing.application;

import com.snails.ecommerce.listing.domain.ImageAsset;
import com.snails.ecommerce.listing.domain.ImageVersion;
import com.snails.ecommerce.listing.domain.ListingBriefVersion;
import com.snails.ecommerce.template.domain.CategoryTemplate;
import java.util.List;

/**
 * 图片资产生成端口。
 *
 * <p>应用层通过该端口调用图片生成能力，后续真实图片模型适配器应实现此接口，
 * 避免业务服务直接依赖具体供应商 SDK。</p>
 */
public interface ImageAssetGenerator {

    /**
     * 根据图片版本、已批准 Brief 和类目模板生成图片资产草稿。
     *
     * @param imageVersion 图片版本草稿
     * @param brief 已批准 Brief 版本
     * @param template 类目模板
     * @return 图片资产草稿，资产 ID、图片版本 ID 和组内排序由应用服务统一补齐
     */
    List<ImageAsset> generateImageAssets(
            ImageVersion imageVersion,
            ListingBriefVersion brief,
            CategoryTemplate template);
}
