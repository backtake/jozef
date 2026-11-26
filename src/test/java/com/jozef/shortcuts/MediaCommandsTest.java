package com.jozef.shortcuts;

import com.jozef.automation.actions.MediaControlAction;
import com.jozef.core.action.ActionContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MediaCommandsTest {

    private final MediaCommands media = new MediaCommands();

    @Test
    void shortPhrasesMapToMediaKeys() {
        assertThat(media.resolve("Głośniej!")).get().satisfies(r -> {
            assertThat(r.call().name()).isEqualTo("media_control");
            assertThat(r.call().arguments()).containsEntry("action", "volume_up");
        });
        assertThat(media.resolve("Józef, następna piosenka")).get()
                .satisfies(r -> assertThat(r.call().arguments()).containsEntry("action", "next"));
        assertThat(media.resolve("pauza")).isPresent();
    }

    @Test
    void longerRequestsGoToTheModel() {
        assertThat(media.resolve("puść coś spokojnego do pracy")).isEmpty();
        assertThat(media.resolve("stop")).isEmpty(); // "stop" also cancels confirmations — not a media key
        assertThat(media.resolve("dalej")).isEmpty();
    }

    @Test
    void actionSendsOnlyWhitelistedKeyCodes() {
        List<int[]> sent = new ArrayList<>();
        MediaControlAction action = new MediaControlAction((vk, times) -> sent.add(new int[]{vk, times}));

        assertThat(action.execute(ActionContext.of(Map.of("action", "volume_up", "steps", 3))).success()).isTrue();
        assertThat(action.execute(ActionContext.of(Map.of("action", "next"))).success()).isTrue();
        assertThat(action.execute(ActionContext.of(Map.of("action", "format_c"))).success()).isFalse();

        assertThat(sent).hasSize(2);
        assertThat(sent.get(0)).containsExactly(0xAF, 3);
        assertThat(sent.get(1)).containsExactly(0xB0, 1);
    }
}
