package com.jozef;

import com.jozef.bootstrap.JozefApplication;
import com.jozef.bootstrap.JozefBootstrapRunner;
import com.jozef.llm.FallbackLlmProvider;
import com.jozef.llm.LLMCapability;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the full Spring context (all beans, capabilities and the new
 * configuration bindings) can be constructed without errors. The bootstrap
 * runner is mocked so the voice loop is never started during the test.
 */
@SpringBootTest(classes = JozefApplication.class)
class ContextLoadTest {

    @MockBean
    private JozefBootstrapRunner bootstrapRunner;

    @Autowired
    private LLMCapability llmCapability;

    @Test
    void contextLoads() {
        // Reaching this point means bean wiring and configuration binding succeeded.
    }

    @Test
    void fallbackProviderIsConfigured() {
        // Test resources configure primary=ollama with fallback=deepseek.
        assertThat(llmCapability.provider()).isInstanceOf(FallbackLlmProvider.class);
    }
}
