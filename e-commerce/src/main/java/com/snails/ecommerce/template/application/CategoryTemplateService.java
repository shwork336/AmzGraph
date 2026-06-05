package com.snails.ecommerce.template.application;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.template.domain.CategoryTemplate;
import com.snails.ecommerce.template.infrastructure.CategoryTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
/**
 * 类目模板应用服务。
 *
 * <p>封装模板查询和错误处理，避免上层流程直接依赖仓储细节。</p>
 */
public class CategoryTemplateService {

    private final CategoryTemplateRepository repository;

    /**
     * 获取启用中的类目模板。
     *
     * @throws BusinessException 当模板不存在或未启用时抛出
     */
    public CategoryTemplate getEnabledTemplate(String categoryCode, String marketplace, String language) {
        return repository.findByCategoryCodeAndMarketplaceAndLanguageAndEnabledTrue(categoryCode, marketplace, language)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TEMPLATE_NOT_FOUND,
                        "Category template not found: " + categoryCode + "/" + marketplace + "/" + language));
    }
}
