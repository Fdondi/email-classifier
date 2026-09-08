package com.emailtool.normalize;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HiddenContentStripperTest {

    private final HiddenContentStripper stripper = new HiddenContentStripper();

    @Test
    void stripsHiddenAndScriptContentFromClassifierText() throws Exception {
        String html = new String(
                getClass().getResourceAsStream("/xss/hidden.html").readAllBytes(), StandardCharsets.UTF_8);
        String text = stripper.toVisibleText(html);
        assertTrue(text.contains("Visible"), text);
        assertFalse(text.contains("hidden-display-none"), text);
        assertFalse(text.contains("hidden-visibility"), text);
        assertFalse(text.contains("hidden-font"), text);
        assertFalse(text.contains("hidden-opacity"), text);
        assertFalse(text.contains("hidden-attr"), text);
        assertFalse(text.contains("hidden-aria"), text);
        assertFalse(text.contains("hidden-script"), text);
        assertFalse(text.contains("IGNORE INSTRUCTIONS"), text);
    }
}
