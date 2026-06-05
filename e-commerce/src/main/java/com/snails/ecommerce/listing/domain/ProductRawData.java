package com.snails.ecommerce.listing.domain;

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
@Table(name = "product_raw_data")
/**
 * 运营上传的产品资料解析结果。
 *
 * <p>第一阶段先以 JSON 字符串保存结构化字段，后续可以按查询需求再拆分或映射为 JSONB。</p>
 */
public class ProductRawData {

    /** 产品资料记录 ID。 */
    @Id
    @Column(length = 64)
    private String rawDataId;

    /** 所属任务 ID。 */
    @Column(nullable = false, length = 64)
    private String taskId;

    /** 产品名称。 */
    @Column(length = 255)
    private String productName;

    /** 品牌名称。 */
    @Column(length = 128)
    private String brandName;

    /** 类目代码。 */
    @Column(length = 64)
    private String categoryCode;

    /** 站点市场。 */
    @Column(length = 32)
    private String marketplace;

    /** 语言。 */
    @Column(length = 32)
    private String language;

    /** 产品规格 JSON。 */
    @Column(columnDefinition = "text")
    private String specificationsJson;

    /** 核心功能 JSON。 */
    @Column(columnDefinition = "text")
    private String coreFunctionsJson;

    /** 包装清单 JSON。 */
    @Column(columnDefinition = "text")
    private String packageItemsJson;

    /** 兼容信息 JSON。 */
    @Column(columnDefinition = "text")
    private String compatibilityInfoJson;

    /** 禁用或需规避的声明 JSON。 */
    @Column(columnDefinition = "text")
    private String forbiddenClaimsJson;
}
