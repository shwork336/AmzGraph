package com.snails.ecommerce.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
/**
 * 本地磁盘文件存储实现。
 *
 * <p>第一阶段用于保存上传的 Markdown、产品图和后续生成文件。后续切换 OSS/S3 时只需要替换
 * {@link FileStoragePort} 实现。</p>
 */
public class LocalFileStorage implements FileStoragePort {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final Path root;

    /**
     * @param localRoot 本地存储根目录，来自 {@code app.storage.local-root}
     */
    public LocalFileStorage(@Value("${app.storage.local-root:./storage}") String localRoot) {
        this.root = Path.of(localRoot).toAbsolutePath().normalize();
    }

    /**
     * 按命名空间和日期分目录保存文件。
     */
    @Override
    public StoredFile save(String namespace, String originalFilename, String contentType, InputStream inputStream) {
        String safeNamespace = cleanPathSegment(namespace);
        String safeFilename = cleanFilename(originalFilename);
        String dateSegment = LocalDate.now().format(DATE_FORMATTER);
        String fileKey = safeNamespace + "/" + dateSegment + "/" + UUID.randomUUID() + "-" + safeFilename;
        Path target = resolveStoragePath(fileKey);

        try {
            Files.createDirectories(target.getParent());
            long size = Files.copy(inputStream, target);
            return new StoredFile(fileKey, safeFilename, contentType, size);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save file: " + safeFilename, e);
        }
    }

    /**
     * 读取文件前会校验 fileKey 不越过存储根目录。
     */
    @Override
    public InputStream read(String fileKey) {
        try {
            return Files.newInputStream(resolveStoragePath(fileKey));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read file: " + fileKey, e);
        }
    }

    /**
     * 第一阶段返回本地路径字符串，前端可用于追踪；后续云存储实现可返回临时访问 URL。
     */
    @Override
    public String resolveUrl(String fileKey) {
        return resolveStoragePath(fileKey).toString();
    }

    /**
     * 解析并校验文件路径，防止通过相对路径访问存储根目录之外的文件。
     */
    private Path resolveStoragePath(String fileKey) {
        Path resolved = root.resolve(fileKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid file key: " + fileKey);
        }
        return resolved;
    }

    /**
     * 清洗命名空间，只保留路径安全字符。
     */
    private String cleanPathSegment(String value) {
        String cleaned = StringUtils.hasText(value) ? value.trim() : "default";
        return cleaned.replace("\\", "/").replaceAll("[^a-zA-Z0-9/_-]", "_");
    }

    /**
     * 清洗文件名，避免用户上传文件名中包含路径或特殊字符。
     */
    private String cleanFilename(String value) {
        String cleaned = StringUtils.hasText(value) ? Path.of(value).getFileName().toString() : "file";
        return cleaned.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
