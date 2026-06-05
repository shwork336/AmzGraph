package com.snails.ecommerce.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.template.domain.CategoryTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CategoryTemplateServiceTest {

    @Autowired
    private CategoryTemplateService service;

    @Test
    void getsEnabledCarStereoTemplate() {
        CategoryTemplate template = service.getEnabledTemplate("CAR_STEREO", "US", "en-US");

        assertThat(template.getCategoryCode()).isEqualTo("CAR_STEREO");
        assertThat(template.getMarketplace()).isEqualTo("US");
        assertThat(template.getLanguage()).isEqualTo("en-US");
        assertThat(template.isEnabled()).isTrue();
        assertThat(template.getDefaultImageAssetTypesJson()).contains("MAIN_IMAGE", "A_PLUS_MODULE");
    }

    @Test
    void throwsBusinessExceptionWhenTemplateIsMissing() {
        assertThatThrownBy(() -> service.getEnabledTemplate("UNKNOWN", "US", "en-US"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TEMPLATE_NOT_FOUND);
    }
}
