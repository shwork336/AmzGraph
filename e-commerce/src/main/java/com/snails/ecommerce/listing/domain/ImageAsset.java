package com.snails.ecommerce.listing.domain;

import com.snails.ecommerce.template.domain.ImageAssetType;
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
@Table(name = "image_asset")
/**
 * 单张图片资产。
 *
 * <p>图片资产挂在 ImageVersion 下，用类型、尺寸、生成 URL 和合规结果描述一张具体图片。</p>
 */
public class ImageAsset {

    /** 图片资产 ID。 */
    @Id
    @Column(length = 64)
    private String assetId;

    /** 所属图片版本 ID。 */
    @Column(nullable = false, length = 64)
    private String imageVersionId;

    /** 图片用途类型。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ImageAssetType type;

    /** 系统生成并提交给图片供应商的原始 Prompt。 */
    @Column(columnDefinition = "text")
    private String prompt;

    /** 图片供应商或大模型返回的改写后 Prompt，用于审计和复现生成过程。 */
    @Column(columnDefinition = "text")
    private String rewrittenPrompt;

    /** 生成图片 URL。 */
    @Column(columnDefinition = "text")
    private String generatedImageUrl;

    /** 可编辑源文件 URL。 */
    @Column(columnDefinition = "text")
    private String sourceEditableFileUrl;

    /** 尺寸配置档位，例如 MAIN_IMAGE 或 A_PLUS_STANDARD。 */
    @Column(length = 64)
    private String sizeProfile;

    /** 目标宽度。 */
    private Integer targetWidth;

    /** 目标高度。 */
    private Integer targetHeight;

    /** 合规状态，例如 PASS、WARNING、FAIL。 */
    @Column(length = 40)
    private String complianceStatus;

    /** 合规检测方法 JSON。 */
    @Column(columnDefinition = "text")
    private String complianceMethodsJson;

    /** 合规问题 JSON。 */
    @Column(columnDefinition = "text")
    private String complianceIssuesJson;

    /** 人工确认人。 */
    @Column(length = 128)
    private String complianceReviewedBy;

    /** 人工确认时间。 */
    private LocalDateTime complianceReviewedAt;

    /** 图片组内排序。 */
    private Integer sortOrder;

    /** 创建时间。 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 新建图片资产时自动填充创建时间。 */
    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
