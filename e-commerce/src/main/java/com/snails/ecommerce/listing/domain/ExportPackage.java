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
@Table(name = "export_package")
/**
 * 导出交付包。
 *
 * <p>任务完成后可导出 ZIP、Excel、Markdown 或 Word。导出包只引用最终选中的图文版本。</p>
 */
public class ExportPackage {

    /** 导出包 ID。 */
    @Id
    @Column(length = 64)
    private String exportPackageId;

    /** 所属任务 ID。 */
    @Column(nullable = false, length = 64)
    private String taskId;

    /** 导出时选中的文案版本 ID。 */
    @Column(length = 64)
    private String selectedTextVersionId;

    /** 导出时选中的图片版本 ID。 */
    @Column(length = 64)
    private String selectedImageVersionId;

    /** 导出格式。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ExportFormat format;

    /** 导出文件 URL。 */
    @Column(columnDefinition = "text")
    private String fileUrl;

    /** manifest 文件 URL。 */
    @Column(columnDefinition = "text")
    private String manifestUrl;

    /** 导出状态，例如 PENDING、RUNNING、SUCCEEDED、FAILED。 */
    @Column(length = 40)
    private String status;

    /** 包含的图片资产 ID 列表 JSON。 */
    @Column(columnDefinition = "text")
    private String includedAssetIdsJson;

    /** 创建时间。 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 新建导出包时自动填充创建时间。 */
    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
