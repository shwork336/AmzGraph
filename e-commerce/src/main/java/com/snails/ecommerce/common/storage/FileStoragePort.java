package com.snails.ecommerce.common.storage;

import java.io.InputStream;

/**
 * 文件存储端口。
 *
 * <p>业务代码依赖该端口，不直接依赖本地磁盘、OSS 或 S3 的具体实现。</p>
 */
public interface FileStoragePort {

    /**
     * 保存文件并返回可追踪的文件元数据。
     */
    StoredFile save(String namespace, String originalFilename, String contentType, InputStream inputStream);

    /**
     * 按文件键读取文件内容。
     */
    InputStream read(String fileKey);

    /**
     * 将文件键解析为当前存储实现下可展示或可追踪的路径。
     */
    String resolveUrl(String fileKey);
}
