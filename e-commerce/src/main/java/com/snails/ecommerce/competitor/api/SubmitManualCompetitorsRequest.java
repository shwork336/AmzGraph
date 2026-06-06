package com.snails.ecommerce.competitor.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 批量手工补录竞品快照请求。
 *
 * @param createdBy 执行补录的操作人
 * @param snapshots 本次提交的竞品快照
 */
public record SubmitManualCompetitorsRequest(
        @NotBlank String createdBy,
        @NotEmpty List<@Valid ManualCompetitorSnapshotRequest> snapshots
) {
}
