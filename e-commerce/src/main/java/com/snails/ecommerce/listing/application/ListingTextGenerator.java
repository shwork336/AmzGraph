package com.snails.ecommerce.listing.application;

import com.snails.ecommerce.listing.domain.ListingBriefVersion;
import com.snails.ecommerce.listing.domain.TextVersion;
import com.snails.ecommerce.template.domain.CategoryTemplate;

/**
 * Listing 文案生成端口。
 *
 * <p>应用层通过该端口调用文案生成能力，后续真实 Spring AI Alibaba 适配器应实现此接口，
 * 避免业务服务直接依赖具体模型 SDK。</p>
 */
public interface ListingTextGenerator {

    /**
     * 根据已批准 Brief 和类目模板生成 Listing 文案。
     *
     * @param brief 已批准 Brief 版本
     * @param template 类目模板
     * @param iterationPrompt 本次迭代附加 Prompt，首版生成可为空
     * @return 文案版本草稿，版本 ID、任务 ID、Brief 版本 ID 由应用服务统一补齐
     */
    TextVersion generateText(ListingBriefVersion brief, CategoryTemplate template, String iterationPrompt);
}
