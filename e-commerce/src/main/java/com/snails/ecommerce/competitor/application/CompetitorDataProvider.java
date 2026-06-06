package com.snails.ecommerce.competitor.application;

import com.snails.ecommerce.competitor.domain.CompetitorSourceType;
import java.util.List;

/**
 * 外部竞品数据源端口。
 *
 * <p>业务层只依赖标准化数据，不直接依赖 Bright Data 的请求参数和响应字段。</p>
 */
public interface CompetitorDataProvider {

    /**
     * 返回该提供者对应的数据来源类型。
     */
    CompetitorSourceType sourceType();

    /**
     * 为任务中的 ASIN 获取标准化竞品数据。
     *
     * @param taskId Listing 任务 ID
     * @param asins 待采集的 ASIN
     * @return 标准化竞品数据
     */
    List<CompetitorSnapshotData> fetch(String taskId, List<String> asins);
}
