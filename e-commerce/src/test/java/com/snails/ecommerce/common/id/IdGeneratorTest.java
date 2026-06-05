package com.snails.ecommerce.common.id;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdGeneratorTest {

    @Test
    void generatesIdWithPrefix() {
        IdGenerator idGenerator = new IdGenerator();

        String id = idGenerator.generate("task");

        assertThat(id).startsWith("task_");
        assertThat(id.length()).isGreaterThan("task_".length());
    }
}
