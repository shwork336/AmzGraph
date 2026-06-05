package com.snails.ecommerce.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void createsSuccessfulResponse() {
        ApiResponse<String> response = ApiResponse.ok("task_1");

        assertThat(response.success()).isTrue();
        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.message()).isEqualTo("OK");
        assertThat(response.data()).isEqualTo("task_1");
    }

    @Test
    void businessExceptionCarriesErrorCode() {
        BusinessException exception = new BusinessException(ErrorCode.TASK_NOT_FOUND, "Task does not exist");

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("Task does not exist");
    }
}
