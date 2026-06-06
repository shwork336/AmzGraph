package com.snails.ecommerce.competitor.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.snails.ecommerce.competitor.domain.CompetitorSnapshot;
import com.snails.ecommerce.competitor.domain.CompetitorSourceType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 竞品快照持久化测试。
 *
 * <p>验证标准化字段、可空字段和历史版本稳定排序。</p>
 */
@SpringBootTest
class CompetitorSnapshotRepositoryTest {

    @Autowired
    private CompetitorSnapshotRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void savesManualCompetitorSnapshot() {
        CompetitorSnapshot snapshot = baseSnapshot("competitor_001", "B0FIRST");
        snapshot.setBulletPointsJson("[\"Wireless CarPlay\",\"Android Auto\"]");
        snapshot.setRating(new BigDecimal("4.4"));
        snapshot.setReviewCount(325L);
        snapshot.setReviewPainPointsJson("[\"Installation instructions are unclear\"]");
        snapshot.setKeywordSignalsJson("[\"wireless carplay stereo\"]");

        repository.save(snapshot);

        CompetitorSnapshot saved = repository.findById("competitor_001").orElseThrow();
        assertThat(saved.getSourceType()).isEqualTo(CompetitorSourceType.MANUAL);
        assertThat(saved.getCreatedBy()).isEqualTo("operator@example.com");
        assertThat(saved.getBulletPointsJson()).contains("Wireless CarPlay");
        assertThat(saved.getCapturedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void allowsOptionalFieldsToBeNull() {
        CompetitorSnapshot snapshot = baseSnapshot("competitor_optional", "B0SECOND");
        snapshot.setBulletPointsJson(null);
        snapshot.setRating(null);
        snapshot.setReviewCount(null);
        snapshot.setReviewPainPointsJson(null);
        snapshot.setKeywordSignalsJson(null);
        snapshot.setRawResponseFileKey(null);

        repository.save(snapshot);

        CompetitorSnapshot saved = repository.findById("competitor_optional").orElseThrow();
        assertThat(saved.getRating()).isNull();
        assertThat(saved.getReviewCount()).isNull();
        assertThat(saved.getRawResponseFileKey()).isNull();
    }

    @Test
    void listsSnapshotsByCapturedAtAndSnapshotIdDescending() {
        CompetitorSnapshot first = baseSnapshot("competitor_001", "B0FIRST");
        first.setCapturedAt(LocalDateTime.of(2026, 6, 6, 10, 0));
        repository.save(first);

        CompetitorSnapshot second = baseSnapshot("competitor_002", "B0FIRST");
        second.setCapturedAt(LocalDateTime.of(2026, 6, 6, 11, 0));
        repository.save(second);

        CompetitorSnapshot sameTimeHigherId = baseSnapshot("competitor_003", "B0SECOND");
        sameTimeHigherId.setCapturedAt(LocalDateTime.of(2026, 6, 6, 11, 0));
        repository.save(sameTimeHigherId);

        List<CompetitorSnapshot> snapshots =
                repository.findByTaskIdOrderByCapturedAtDescSnapshotIdDesc("task_competitor");

        assertThat(snapshots)
                .extracting(CompetitorSnapshot::getSnapshotId)
                .containsExactly("competitor_003", "competitor_002", "competitor_001");
    }

    private CompetitorSnapshot baseSnapshot(String snapshotId, String asin) {
        CompetitorSnapshot snapshot = new CompetitorSnapshot();
        snapshot.setSnapshotId(snapshotId);
        snapshot.setTaskId("task_competitor");
        snapshot.setAsin(asin);
        snapshot.setTitle("9 Inch Wireless Car Stereo");
        snapshot.setSourceType(CompetitorSourceType.MANUAL);
        snapshot.setSourceName("Manual Entry");
        snapshot.setCreatedBy("operator@example.com");
        return snapshot;
    }
}
