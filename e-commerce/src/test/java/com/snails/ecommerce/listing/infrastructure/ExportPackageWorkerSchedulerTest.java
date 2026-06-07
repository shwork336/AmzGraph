package com.snails.ecommerce.listing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.snails.ecommerce.listing.application.ExportPackageWorkerService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 导出 Worker 调度适配器测试。
 *
 * <p>验证调度器默认关闭，只有显式配置开启时才加载，避免测试和本地环境自动执行导出。</p>
 */
class ExportPackageWorkerSchedulerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    ExportPackageWorkerScheduler.class,
                    TestWorkerConfiguration.class);

    @Test
    void doesNotLoadSchedulerByDefault() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(ExportPackageWorkerScheduler.class));
    }

    @Test
    void loadsSchedulerWhenEnabled() {
        contextRunner
                .withPropertyValues("listing.export-worker.enabled=true")
                .run(context ->
                        assertThat(context).hasSingleBean(ExportPackageWorkerScheduler.class));
    }

    @TestConfiguration
    static class TestWorkerConfiguration {

        @Bean
        ExportPackageWorkerService exportPackageWorkerService() {
            return org.mockito.Mockito.mock(ExportPackageWorkerService.class);
        }
    }
}
