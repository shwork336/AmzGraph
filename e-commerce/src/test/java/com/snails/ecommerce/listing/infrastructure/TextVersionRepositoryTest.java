package com.snails.ecommerce.listing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.snails.ecommerce.listing.domain.TextVersion;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TextVersionRepositoryTest {

    @Autowired
    private TextVersionRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void savesTextVersion() {
        TextVersion version = baseVersion("text_001", "task_text");
        version.setTitle("Wireless CarPlay Stereo for Older Vehicles");
        version.setBulletPointsJson("[\"Wireless CarPlay\",\"Android Auto\"]");
        version.setDescription("Upgrade an older vehicle with modern smartphone connectivity.");
        version.setBackendSearchTerms("wireless carplay stereo android auto");
        version.setTargetKeywordsJson("[\"car stereo\",\"wireless carplay\"]");
        version.setComplianceWarningsJson("[]");
        version.setQualityScore(82);

        repository.save(version);

        TextVersion saved = repository.findById("text_001").orElseThrow();
        assertThat(saved.getTaskId()).isEqualTo("task_text");
        assertThat(saved.getBriefVersionId()).isEqualTo("brief_text");
        assertThat(saved.getTitle()).contains("Wireless CarPlay");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void listsVersionsByCreatedAtAndVersionIdDescending() {
        TextVersion first = baseVersion("text_001", "task_text");
        first.setCreatedAt(LocalDateTime.of(2026, 6, 6, 10, 0));
        repository.save(first);

        TextVersion second = baseVersion("text_002", "task_text");
        second.setCreatedAt(LocalDateTime.of(2026, 6, 6, 11, 0));
        repository.save(second);

        TextVersion sameTimeHigherId = baseVersion("text_003", "task_text");
        sameTimeHigherId.setCreatedAt(LocalDateTime.of(2026, 6, 6, 11, 0));
        repository.save(sameTimeHigherId);

        TextVersion otherTask = baseVersion("text_other", "task_other");
        otherTask.setCreatedAt(LocalDateTime.of(2026, 6, 6, 12, 0));
        repository.save(otherTask);

        List<TextVersion> versions =
                repository.findByTaskIdOrderByCreatedAtDescVersionIdDesc("task_text");

        assertThat(versions)
                .extracting(TextVersion::getVersionId)
                .containsExactly("text_003", "text_002", "text_001");
    }

    @Test
    void findsLatestVersionByCreatedAtAndVersionIdDescending() {
        TextVersion first = baseVersion("text_001", "task_text");
        first.setCreatedAt(LocalDateTime.of(2026, 6, 6, 10, 0));
        repository.save(first);

        TextVersion latest = baseVersion("text_002", "task_text");
        latest.setCreatedAt(LocalDateTime.of(2026, 6, 6, 11, 0));
        repository.save(latest);

        TextVersion found = repository
                .findTopByTaskIdOrderByCreatedAtDescVersionIdDesc("task_text")
                .orElseThrow();

        assertThat(found.getVersionId()).isEqualTo("text_002");
    }

    private TextVersion baseVersion(String versionId, String taskId) {
        TextVersion version = new TextVersion();
        version.setVersionId(versionId);
        version.setTaskId(taskId);
        version.setBriefVersionId("brief_text");
        version.setIterationPrompt(null);
        version.setTitle("Wireless CarPlay Stereo");
        version.setBulletPointsJson("[\"Wireless CarPlay\"]");
        version.setDescription("A practical stereo upgrade for Amazon US buyers.");
        version.setBackendSearchTerms("car stereo wireless carplay");
        version.setTargetKeywordsJson("[\"car stereo\"]");
        version.setComplianceWarningsJson("[]");
        version.setSelected(false);
        return version;
    }
}
