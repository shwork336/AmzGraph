package com.snails.ecommerce.template.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "category_template")
/**
 * 类目模板。
 *
 * <p>类目模板承载字段要求、Prompt 片段、图片类型、尺寸策略和合规规则。业务流程只读取模板，
 * 不把 Car Stereo 等类目规则写死在任务代码中。</p>
 */
public class CategoryTemplate {

    /** 模板 ID，例如 tpl_car_stereo_us_en。 */
    @Id
    @Column(length = 64)
    private String templateId;

    /** 类目代码，第一版默认 CAR_STEREO。 */
    @Column(nullable = false, length = 64)
    private String categoryCode;

    /** 类目展示名称。 */
    @Column(nullable = false, length = 128)
    private String categoryName;

    /** 站点市场，例如 US。 */
    @Column(nullable = false, length = 32)
    private String marketplace;

    /** 生成语言，例如 en-US。 */
    @Column(nullable = false, length = 32)
    private String language;

    /** 必填产品字段 JSON。 */
    @Column(columnDefinition = "text")
    private String requiredProductFieldsJson;

    /** 可选产品字段 JSON。 */
    @Column(columnDefinition = "text")
    private String optionalProductFieldsJson;

    /** 默认图片资产类型 JSON。 */
    @Column(columnDefinition = "text")
    private String defaultImageAssetTypesJson;

    /** 图片尺寸配置 JSON。 */
    @Column(columnDefinition = "text")
    private String sizeProfilesJson;

    /** Prompt 片段 JSON。 */
    @Column(columnDefinition = "text")
    private String promptFragmentsJson;

    /** 合规规则 JSON。 */
    @Column(columnDefinition = "text")
    private String complianceRulesJson;

    /** 是否启用该模板。 */
    @Column(nullable = false)
    private boolean enabled;
}
