package com.snails.ecommerce.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "text_version")
/**
 * Listing 文案版本。
 *
 * <p>文案版本与图片版本分轨管理，最终归档时只选择一个文案版本。</p>
 */
public class TextVersion {

    /** 文案版本 ID。 */
    @Id
    @Column(length = 64)
    private String versionId;

    /** 所属任务 ID。 */
    @Column(nullable = false, length = 64)
    private String taskId;

    /** 父文案版本 ID，用于表达迭代关系。 */
    @Column(length = 64)
    private String parentVersionId;

    /** 生成该文案使用的 Brief 版本 ID。 */
    @Column(length = 64)
    private String briefVersionId;

    /** 本次迭代附加 Prompt。 */
    @Column(columnDefinition = "text")
    private String iterationPrompt;

    /** Amazon Listing 标题。 */
    @Column(columnDefinition = "text")
    private String title;

    /** 5 点 Bullet Points JSON。 */
    @Column(columnDefinition = "text")
    private String bulletPointsJson;

    /** 产品描述。 */
    @Column(columnDefinition = "text")
    private String description;

    /** 后台搜索词。 */
    @Column(columnDefinition = "text")
    private String backendSearchTerms;

    /** 目标关键词 JSON。 */
    @Column(columnDefinition = "text")
    private String targetKeywordsJson;

    /** 文案合规警告 JSON。 */
    @Column(columnDefinition = "text")
    private String complianceWarningsJson;

    /** 质量评分，第一阶段仅预留字段。 */
    private Integer qualityScore;

    /** 是否为最终选中版本。 */
    @Column(nullable = false)
    private boolean selected;

    /** 创建时间。 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 新建文案版本时自动填充创建时间。 */
    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
