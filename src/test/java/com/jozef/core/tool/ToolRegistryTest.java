package com.jozef.core.tool;

import com.jozef.core.action.Action;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistryTest {

    private static Action echoAction() {
        return new Action() {
            @Override public String name() { return "echo"; }
            @Override public String description() { return "Echoes the input"; }
            @Override public ActionResult execute(ActionContext context) {
                return ActionResult.ok("echo: " + context.getString("value"));
            }
        };
    }

    @Test
    void executesRegisteredTool() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(echoAction());

        ActionResult result = registry.execute("echo", Map.of("value", "hi"));

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("echo: hi");
    }

    @Test
    void returnsFailureForUnknownTool() {
        ToolRegistry registry = new ToolRegistry();

        ActionResult result = registry.execute("nope", Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("Unknown tool");
    }

    @Test
    void exposesToolDefinitions() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(echoAction());

        var definitions = registry.definitions();

        assertThat(definitions).hasSize(1);
        assertThat(definitions.get(0).name()).isEqualTo("echo");
        assertThat(definitions.get(0).description()).isEqualTo("Echoes the input");
    }

    @Test
    void knowsAndNamesReflectRegistry() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(echoAction());

        assertThat(registry.knows("echo")).isTrue();
        assertThat(registry.knows("missing")).isFalse();
        assertThat(registry.names()).containsExactly("echo");
        assertThat(registry.size()).isEqualTo(1);
    }
}
