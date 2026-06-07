package com.snails.ecommerce.listing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.snails.ecommerce.listing.domain.GenerationStatus;
import com.snails.ecommerce.listing.domain.ImageVersion;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 图片版本持久化测试。
 *
 * <p>验证图片版本保存、任务维度历史查询和最新版本稳定排序。</p>
 */
@SpringBootTest
class ImageVersionRepositoryTest {

    @Autowired
    private ImageVersionRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void savesImageVersion() {
        ImageVersion version = baseVersion("image_001", "task_image");
        version.setImageProvider("PLACEHOLDER");
        version.setImageModel("placeholder-image-model");
        version.setGenerationParamsJson("{\"quality\":\"draft\"}");
        version.setQualityScore(80);

        repository.save(version);

        ImageVersion saved = repository.findById("image_001").orElseThrow();
        assertThat(saved.getTaskId()).isEqualTo("task_image");
        assertThat(saved.getBriefVersionId()).isEqualTo("brief_image");
        assertThat(saved.getStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void listsVersionsByCreatedAtAndVersionIdDescending() {
        ImageVersion first = baseVersion("image_001", "task_image");
        first.setCreatedAt(LocalDateTime.of(2026, 6, 6, 10, 0));
        repository.save(first);

        ImageVersion second = baseVersion("image_002", "task_image");
        second.setCreatedAt(LocalDateTime.of(2026, 6, 6, 11, 0));
        repository.save(second);

        ImageVersion sameTimeHigherId = baseVersion("image_003", "task_image");
        sameTimeHigherId.setCreatedAt(LocalDateTime.of(2026, 6, 6, 11, 0));
        repository.save(sameTimeHigherId);

        ImageVersion otherTask = baseVersion("image_other", "task_other");
        otherTask.setCreatedAt(LocalDateTime.of(2026, 6, 6, 12, 0));
        repository.save(otherTask);

        List<ImageVersion> versions =
                repository.findByTaskIdOrderByCreatedAtDescVersionIdDesc("task_image");

        assertThat(versions)
                .extracting(ImageVersion::getVersionId)
                .containsExactly("image_003", "image_002", "image_001");
    }

    @Test
    void findsLatestVersionByCreatedAtAndVersionIdDescending() {
        ImageVersion first = baseVersion("image_001", "task_image");
        first.setCreatedAt(LocalDateTime.of(2026, 6, 6, 10, 0));
        repository.save(first);

        ImageVersion latest = baseVersion("image_002", "task_image");
        latest.setCreatedAt(LocalDateTime.of(2026, 6, 6, 11, 0));
        repository.save(latest);

        ImageVersion found = repository
                .findTopByTaskIdOrderByCreatedAtDescVersionIdDesc("task_image")
                .orElseThrow();

        assertThat(found.getVersionId()).isEqualTo("image_002");
    }

    @Test
    void findsVersionsByTaskId() {
        repository.save(baseVersion("image_001", "task_image"));
        repository.save(baseVersion("image_002", "task_image"));
        repository.save(baseVersion("image_other", "task_other"));

        List<ImageVersion> versions = repository.findByTaskId("task_image");

        assertThat(versions)
                .extracting(ImageVersion::getVersionId)
                .containsExactlyInAnyOrder("image_001", "image_002");
    }

    private ImageVersion baseVersion(String versionId, String taskId) {
        ImageVersion version = new ImageVersion();
        version.setVersionId(versionId);
        version.setTaskId(taskId);
        version.setBriefVersionId("brief_image");
        version.setInputProductUrlsJson("[\"uploads/product-images/product.png\"]");
        version.setImageProvider("PLACEHOLDER");
        version.setImageModel("placeholder-image-model");
        version.setGenerationParamsJson("{}");
        version.setStatus(GenerationStatus.SUCCEEDED);
        version.setSelected(false);
        return version;
    }
}
