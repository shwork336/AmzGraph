package com.snails.ecommerce.common.api;

import java.util.List;

/**
 * 通用分页响应。
 *
 * <p>用于需要返回列表和分页元数据的查询接口。当前只承载已过滤后的当前页数据，
 * 不绑定具体领域对象，避免各模块重复定义分页结构。</p>
 *
 * @param items 当前页数据
 * @param page 当前页码，从 0 开始
 * @param size 页大小
 * @param totalItems 过滤后的总记录数
 * @param totalPages 总页数
 * @param hasNext 是否存在下一页
 * @param hasPrevious 是否存在上一页
 * @param <T> 列表项类型
 */
public record PagedResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {
}
