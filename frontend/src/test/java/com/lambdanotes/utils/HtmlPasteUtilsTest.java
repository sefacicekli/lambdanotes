package com.lambdanotes.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HtmlPasteUtilsTest {

    @Test
    public void testCssDetection() {
        String cssCode = "@font-face {\n" +
                "    font-family: 'JetBrains Mono';\n" +
                "    font-weight: bold;\n" +
                "    font-style: italic;\n" +
                "    src: url('fonts/JetBrainsMono-BoldItalic.ttf');\n" +
                "}";

        // Wrap in some HTML as the utility expects HTML
        String html = "<pre>" + cssCode + "</pre>";

        String result = HtmlPasteUtils.convertToMarkdownCodeBlock(html);
        
        // We expect the result to start with ```css
        // Currently it might be failing and returning python or something else
        String firstLine = result.split("\n")[0];
        assertEquals("```css", firstLine, "Expected CSS detection but got: " + firstLine);
    }
}
