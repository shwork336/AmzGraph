package com.snails.ecommerce.listing.application;

/**
 * 导出后台 Worker 单批处理结果。
 *
 * <p>该结果只描述本批扫描和执行统计，不暴露 JPA 实体，供调度器日志、测试和后续运维接口复用。</p>
 *
 * @param recovered 本批执行前恢复为失败的超时记录数
 * @param scanned 本批扫描到的待处理记录数
 * @param claimed 本批成功领取的记录数
 * @param succeeded 本批成功执行的记录数
 * @param failed 本批执行失败的记录数
 */
public record ExportPackageWorkerResult(
        int recovered,
        int scanned,
        int claimed,
        int succeeded,
        int failed
) {
}
