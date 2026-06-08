package com.snails.ecommerce.listing.api;

import com.snails.ecommerce.listing.domain.ExportPackage;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 导出交付包响应。
 *
 * <p>该 DTO 用于导出命令和查询接口，避免 API 直接暴露 JPA 实体。</p>
 *
 * @param exportPackageId 导出包 ID
 * @param taskId 所属任务 ID
 * @param selectedTextVersionId 导出引用的文案版本 ID
 * @param selectedImageVersionId 导出引用的图片版本 ID
 * @param format 导出格式
 * @param status 导出状态
 * @param fileUrl 导出文件 URL
 * @param manifestUrl manifest 文件 URL
 * @param failureReason 失败原因
 * @param canceledBy 取消操作人
 * @param cancelReason 取消原因
 * @param canceledAt 取消时间
 * @param includedAssetIds 纳入导出的图片资产 ID
 * @param createdAt 创建时间
 * @param startedAt 导出开始执行时间
 * @param updatedAt 最后更新时间
 */
public record ExportPackageResponse(
        String exportPackageId,
        String taskId,
        String selectedTextVersionId,
        String selectedImageVersionId,
        String format,
        String status,
        String fileUrl,
        String manifestUrl,
        String failureReason,
        String canceledBy,
        String cancelReason,
        LocalDateTime canceledAt,
        List<String> includedAssetIds,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime updatedAt
) {

    /**
     * 从导出包实体和已解析的资产 ID 列表创建响应。
     */
    public static ExportPackageResponse from(ExportPackage exportPackage, List<String> includedAssetIds) {
        return new ExportPackageResponse(
                exportPackage.getExportPackageId(),
                exportPackage.getTaskId(),
                exportPackage.getSelectedTextVersionId(),
                exportPackage.getSelectedImageVersionId(),
                exportPackage.getFormat().name(),
                exportPackage.getStatus(),
                exportPackage.getFileUrl(),
                exportPackage.getManifestUrl(),
                exportPackage.getFailureReason(),
                exportPackage.getCanceledBy(),
                exportPackage.getCancelReason(),
                exportPackage.getCanceledAt(),
                includedAssetIds,
                exportPackage.getCreatedAt(),
                exportPackage.getStartedAt(),
                exportPackage.getUpdatedAt());
    }
}
