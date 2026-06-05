package com.snails.ecommerce.template.application;

import com.snails.ecommerce.template.domain.CategoryTemplate;
import com.snails.ecommerce.template.infrastructure.CategoryTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
/**
 * Car Stereo 默认模板初始化器。
 *
 * <p>应用启动时写入或更新第一版内置模板，保证开发环境和测试环境都有可用模板。</p>
 */
public class CarStereoTemplateInitializer implements CommandLineRunner {

    /** 第一版内置 Car Stereo 模板 ID。 */
    private static final String TEMPLATE_ID = "tpl_car_stereo_us_en";

    private final CategoryTemplateRepository repository;

    /**
     * 启动时幂等写入模板。已有模板会被更新为当前代码中的基础配置。
     */
    @Override
    public void run(String... args) {
        CategoryTemplate template = repository.findById(TEMPLATE_ID).orElseGet(CategoryTemplate::new);
        template.setTemplateId(TEMPLATE_ID);
        template.setCategoryCode("CAR_STEREO");
        template.setCategoryName("Car Stereo");
        template.setMarketplace("US");
        template.setLanguage("en-US");
        template.setRequiredProductFieldsJson("""
                ["screenSize","screenSpec","systemVersion","carPlay","androidAuto","bluetooth","rearCamera","compatibleVehicles","interfaces","packageItems"]
                """);
        template.setOptionalProductFieldsJson("""
                ["canbus","installationNotes","warrantyInfo","forbiddenClaims"]
                """);
        template.setDefaultImageAssetTypesJson("""
                ["MAIN_IMAGE","INFOGRAPHIC","LIFESTYLE","DIMENSION","COMPATIBILITY","INSTALLATION","PACKAGE_CONTENTS","A_PLUS_MODULE"]
                """);
        template.setSizeProfilesJson("""
                {"MAIN_IMAGE":{"width":2000,"height":2000},"STANDARD_LISTING":{"width":2000,"height":2000},"A_PLUS_STANDARD":{"width":1464,"height":600}}
                """);
        template.setPromptFragmentsJson("""
                ["Amazon US English listing","Car Stereo category","Do not copy competitor wording","Avoid unverified absolute claims"]
                """);
        template.setComplianceRulesJson("""
                ["MAIN_IMAGE_WHITE_BACKGROUND","NO_WATERMARK","NO_UNVERIFIED_CLAIMS","NO_COMPETITOR_LOGO"]
                """);
        template.setEnabled(true);
        repository.save(template);
    }
}
