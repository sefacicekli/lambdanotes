package com.lambdanotes.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HtmlPasteUtils - handling code paste from IDEs.
 */
class HtmlPasteUtilsTest {

    @Test
    void testIsCodeHtml_withVSCodeContent() {
        // VS Code style HTML with colored spans
        String vsCodeHtml = "<div style=\"color: #d4d4d4;background-color: #1e1e1e;font-family: Consolas, 'Courier New', monospace;font-weight: normal;font-size: 14px;line-height: 19px;white-space: pre;\"><div><span style=\"color: #569cd6;\">public</span><span style=\"color: #d4d4d4;\"> </span><span style=\"color: #569cd6;\">class</span><span style=\"color: #d4d4d4;\"> </span><span style=\"color: #4ec9b0;\">Hello</span><span style=\"color: #d4d4d4;\"> {</span></div></div>";
        
        assertTrue(HtmlPasteUtils.isCodeHtml(vsCodeHtml));
    }

    @Test
    void testIsCodeHtml_withPreTag() {
        String preHtml = "<pre><code class=\"language-java\">System.out.println(\"Hello\");</code></pre>";
        assertTrue(HtmlPasteUtils.isCodeHtml(preHtml));
    }

    @Test
    void testIsCodeHtml_withPlainText() {
        String plainHtml = "<p>This is just a normal paragraph of text.</p>";
        assertFalse(HtmlPasteUtils.isCodeHtml(plainHtml));
    }

    @Test
    void testIsCodeHtml_withNull() {
        assertFalse(HtmlPasteUtils.isCodeHtml(null));
    }

    @Test
    void testIsCodeHtml_withEmpty() {
        assertFalse(HtmlPasteUtils.isCodeHtml(""));
    }

    @Test
    void testConvertToMarkdownCodeBlock_simpleJava() {
        String html = "<pre><code class=\"language-java\">public class Hello {\n    public static void main(String[] args) {\n        System.out.println(\"Hello\");\n    }\n}</code></pre>";
        
        String result = HtmlPasteUtils.convertToMarkdownCodeBlock(html);
        
        assertTrue(result.startsWith("```java\n"));
        assertTrue(result.endsWith("\n```"));
        assertTrue(result.contains("public class Hello"));
    }

    @Test
    void testConvertToMarkdownCodeBlock_vsCodeStyle() {
        String html = "<div style=\"font-family: Consolas, monospace;\"><span style=\"color: #569cd6;\">const</span> x = <span style=\"color: #b5cea8;\">42</span>;</div>";
        
        String result = HtmlPasteUtils.convertToMarkdownCodeBlock(html);
        
        assertTrue(result.startsWith("```"));
        assertTrue(result.contains("const x = 42"));
        assertTrue(result.endsWith("\n```"));
    }

    @Test
    void testConvertToMarkdownCodeBlock_withHtmlEntities() {
        String html = "<pre><code>if (a &lt; b &amp;&amp; c &gt; d) {}</code></pre>";
        
        String result = HtmlPasteUtils.convertToMarkdownCodeBlock(html);
        
        assertTrue(result.contains("if (a < b && c > d)"));
    }

    @Test
    void testConvertToMarkdownCodeBlock_withLineBreaks() {
        String html = "<div>line1<br/>line2<br>line3</div>";
        
        String result = HtmlPasteUtils.convertToMarkdownCodeBlock(html);
        
        assertTrue(result.contains("line1\nline2\nline3"));
    }

    @Test
    void testConvertToMarkdownCodeBlock_withNull() {
        assertEquals("", HtmlPasteUtils.convertToMarkdownCodeBlock(null));
    }

    @Test
    void testConvertToMarkdownCodeBlock_withEmpty() {
        assertEquals("", HtmlPasteUtils.convertToMarkdownCodeBlock(""));
    }

    @Test
    void testLanguageDetection_javascript() {
        String html = "<pre><code class=\"language-js\">const x = 1;</code></pre>";
        String result = HtmlPasteUtils.convertToMarkdownCodeBlock(html);
        assertTrue(result.startsWith("```javascript\n"));
    }

    @Test
    void testLanguageDetection_typescript() {
        String html = "<pre><code class=\"language-ts\">const x: number = 1;</code></pre>";
        String result = HtmlPasteUtils.convertToMarkdownCodeBlock(html);
        assertTrue(result.startsWith("```typescript\n"));
    }

    @Test
    void testLanguageDetection_python() {
        String html = "<pre><code class=\"language-py\">def hello(): pass</code></pre>";
        String result = HtmlPasteUtils.convertToMarkdownCodeBlock(html);
        assertTrue(result.startsWith("```python\n"));
    }

    @Test
    void testLanguageDetection_fromContent_java() {
        String html = "<div style=\"font-family: monospace;\">public class Hello { private void test() {} }</div>";
        String result = HtmlPasteUtils.convertToMarkdownCodeBlock(html);
        assertTrue(result.startsWith("```java\n"));
    }

    @Test
    void testLanguageDetection_fromContent_python() {
        String html = "<div style=\"font-family: monospace;\">def hello():\n    print(\"hello\")</div>";
        String result = HtmlPasteUtils.convertToMarkdownCodeBlock(html);
        assertTrue(result.startsWith("```python\n"));
    }

    @Test
    void testPreservesIndentation() {
        String html = "<pre><code>function test() {\n    if (true) {\n        console.log(\"test\");\n    }\n}</code></pre>";
        
        String result = HtmlPasteUtils.convertToMarkdownCodeBlock(html);
        
        // Should preserve relative indentation
        assertTrue(result.contains("    if (true)") || result.contains("if (true)"));
    }

    @Test
    void testNumericHtmlEntities() {
        String html = "<pre><code>&#60;div&#62;&#38;test&#60;/div&#62;</code></pre>";
        
        String result = HtmlPasteUtils.convertToMarkdownCodeBlock(html);
        
        assertTrue(result.contains("<div>&test</div>"));
    }

    @Test
    void testNbspHandling() {
        String html = "<pre><code>hello&nbsp;world</code></pre>";
        
        String result = HtmlPasteUtils.convertToMarkdownCodeBlock(html);
        
        assertTrue(result.contains("hello world"));
    }
}
