package com.jozef.speech;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpeechTextCleanerTest {

    @Test
    void handlesNullAndBlank() {
        assertThat(SpeechTextCleaner.clean(null)).isEmpty();
        assertThat(SpeechTextCleaner.clean("   ")).isEmpty();
    }

    @Test
    void stripsMarkdownForSpeech() {
        String markdown = "**Ważne** — zobacz [link](https://example.com) oraz `kod` i:\n\n"
                + "- punkt pierwszy\n"
                + "1. punkt numerowany\n\n"
                + "```java\nSystem.out.println(\"x\");\n```";

        String cleaned = SpeechTextCleaner.clean(markdown);

        assertThat(cleaned).contains("Ważne");
        assertThat(cleaned).contains("link");
        assertThat(cleaned).doesNotContain("**");
        assertThat(cleaned).doesNotContain("[");
        assertThat(cleaned).doesNotContain("```");
        assertThat(cleaned).doesNotContain("System.out.println");
    }

    @Test
    void removesHeadingsAndBullets() {
        String cleaned = SpeechTextCleaner.clean("# Tytuł\n## Podtytuł\n- a\n- b");
        assertThat(cleaned).contains("Tytuł");
        assertThat(cleaned).doesNotContain("#");
        assertThat(cleaned).doesNotContain("- a");
    }

    @Test
    void collapsesWhitespace() {
        assertThat(SpeechTextCleaner.clean("  hello   world  ")).isEqualTo("hello world");
    }
}
