package com.snails.ecommerce.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "listing_task")
/**
 * Listing 资产生产任务。
 *
 * <p>该实体是主流程根记录，保存任务状态、类目模板、输入图片、竞品 ASIN 和最终选中的图文版本。</p>
 */
public class ListingTask {

    /** 任务 ID，对外暴露的业务主键。 */
    @Id
    @Column(length = 64)
    private String taskId;

    /** 任务主状态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ListingTaskStatus status;

    /** 文案生成子流程状态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private GenerationStatus textStatus;

    /** 图片生成子流程状态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private GenerationStatus imageStatus;

    /** Brief 审核状态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private BriefStatus briefStatus;

    /** 类目代码，第一版默认 CAR_STEREO。 */
    @Column(nullable = false, length = 64)
    private String categoryCode;

    /** 当前任务使用的类目模板 ID。 */
    @Column(nullable = false, length = 64)
    private String categoryTemplateId;

    /** Amazon 站点市场，例如 US。 */
    @Column(nullable = false, length = 32)
    private String marketplace;

    /** 生成语言，例如 en-US。 */
    @Column(nullable = false, length = 32)
    private String language;

    /** 原始产品图片 URL 列表 JSON。 */
    @Column(columnDefinition = "text")
    private String originalProductUrlsJson;

    /** 输入的竞品 ASIN 列表 JSON。 */
    @Column(columnDefinition = "text")
    private String competitorAsinsJson;

    /** 最终归档选中的文案版本 ID。 */
    @Column(length = 64)
    private String selectedTextVersionId;

    /** 最终归档选中的图片版本 ID。 */
    @Column(length = 64)
    private String selectedImageVersionId;

    /** 创建时间。 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 最后更新时间。 */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** 新建任务时自动填充创建和更新时间。 */
    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    /** 更新任务时自动刷新更新时间。 */
    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
