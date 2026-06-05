package com.snails.ecommerce.listing.domain;

/**
 * 交付包导出格式。
 */
public enum ExportFormat {
    /** 默认 ZIP 交付包。 */
    ZIP,
    /** Excel 表格。 */
    EXCEL,
    /** Markdown 文档。 */
    MARKDOWN,
    /** Word 文档。 */
    WORD
}
