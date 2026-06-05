package com.snails.ecommerce.template.infrastructure;

import com.snails.ecommerce.template.domain.CategoryTemplate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 类目模板仓储。
 */
public interface CategoryTemplateRepository extends JpaRepository<CategoryTemplate, String> {

    /**
     * 查找指定类目、站点和语言下启用中的模板。
     */
    Optional<CategoryTemplate> findByCategoryCodeAndMarketplaceAndLanguageAndEnabledTrue(
            String categoryCode,
            String marketplace,
            String language);
}
