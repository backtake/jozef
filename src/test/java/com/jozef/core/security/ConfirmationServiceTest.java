package com.jozef.core.security;

import com.jozef.core.action.ExecutionStrategy;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmationServiceTest {

    @Test
    void autoDenyApprovesOnlySafeActions() {
        AutoDenyConfirmationService service = new AutoDenyConfirmationService();

        assertThat(service.requiresConfirmation(ExecutionStrategy.SAFE)).isFalse();
        assertThat(service.confirm("t", Map.of(), ExecutionStrategy.SAFE)).isTrue();

        assertThat(service.requiresConfirmation(ExecutionStrategy.CONFIRM)).isTrue();
        assertThat(service.confirm("t", Map.of(), ExecutionStrategy.CONFIRM)).isFalse();

        assertThat(service.requiresConfirmation(ExecutionStrategy.DANGEROUS)).isTrue();
        assertThat(service.confirm("t", Map.of(), ExecutionStrategy.DANGEROUS)).isFalse();
    }

    @Test
    void consoleServiceRequiresConfirmationForNonSafe() {
        ConsoleConfirmationService service = new ConsoleConfirmationService();

        assertThat(service.requiresConfirmation(ExecutionStrategy.SAFE)).isFalse();
        assertThat(service.requiresConfirmation(ExecutionStrategy.CONFIRM)).isTrue();
        assertThat(service.requiresConfirmation(ExecutionStrategy.DANGEROUS)).isTrue();
    }

    @Test
    void consoleServiceApprovesSafeWithoutPrompt() {
        ConsoleConfirmationService service = new ConsoleConfirmationService();
        assertThat(service.confirm("open_application", Map.of("name", "chrome"), ExecutionStrategy.SAFE)).isTrue();
    }
}
