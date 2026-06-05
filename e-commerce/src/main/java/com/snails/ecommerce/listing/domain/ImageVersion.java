package com.snails.ecommerce.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "image_version")
/**
 * 图片资产组版本。
 *
 * <p>一个 ImageVersion 代表一次图片组生成或迭代，其下挂多个 ImageAsset。</p>
 */
public class ImageVersion {

    /** 图片版本 ID。 */
    @Id
    @Column(length = 64)
    private String versionId;

    /** 所属任务 ID。 */
    @Column(nullable = false, length = 64)
    private String taskId;

    /** 父图片版本 ID，用于表达迭代关系。 */
    @Column(length = 64)
    private String parentVersionId;

    /** 生成该图片版本使用的 Brief 版本 ID。 */
    @Column(length = 64)
    private String briefVersionId;

    /** 本次图片迭代附加 Prompt。 */
    @Column(columnDefinition = "text")
    private String iterationPrompt;

    /** 参考图 URL。 */
    @Column(columnDefinition = "text")
    private String referenceImageUrl;

    /** 输入产品图 URL 列表 JSON。 */
    @Column(columnDefinition = "text")
    private String inputProductUrlsJson;

    /** 图片生成供应商。 */
    @Column(length = 64)
    private String imageProvider;

    /** 图片生成模型。 */
    @Column(length = 128)
    private String imageModel;

    /** 图片生成参数 JSON。 */
    @Column(columnDefinition = "text")
    private String generationParamsJson;

    /** 图片生成状态。 */
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private GenerationStatus status;

    /** 质量评分，第一阶段仅预留字段。 */
    private Integer qualityScore;

    /** 是否为最终选中版本。 */
    @Column(nullable = false)
    private boolean selected;

    /** 创建时间。 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 新建图片版本时自动填充创建时间。 */
    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
