package com.snails.ecommerce.common.storage;

/**
 * 文件保存后的元数据。
 *
 * @param fileKey 存储层内部文件键，用于后续读取和解析 URL
 * @param originalFilename 清洗后的原始文件名
 * @param contentType 上传时声明的 MIME 类型
 * @param size 文件字节数
 */
public record StoredFile(
        String fileKey,
        String originalFilename,
        String contentType,
        long size
) {
}
