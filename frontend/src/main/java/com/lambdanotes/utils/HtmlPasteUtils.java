package com.lambdanotes.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to handle HTML content pasted from IDEs like VS Code, IntelliJ, etc.
 * Converts highlighted code in HTML format to Markdown code blocks.
 */
public class HtmlPasteUtils {

    // Patterns to detect code content from various IDEs
    private static final Pattern VS_CODE_PATTERN = Pattern.compile(
        "<div[^>]*style=\"[^\"]*(?:font-family|background)[^\"]*\"[^>]*>.*?</div>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern PRE_CODE_PATTERN = Pattern.compile(
        "<pre[^>]*>.*?</pre>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern CODE_PATTERN = Pattern.compile(
        "<code[^>]*>.*?</code>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    // Pattern to detect monospace font families (common in code)
    private static final Pattern MONOSPACE_FONT_PATTERN = Pattern.compile(
        "font-family\\s*:\\s*[^;]*(?:monospace|consolas|courier|monaco|menlo|\"?source code|fira code|jetbrains|roboto mono|ubuntu mono|cascadia|hack|inconsolata)",
        Pattern.CASE_INSENSITIVE
    );

    // Pattern to extract language hint from class names
    private static final Pattern LANGUAGE_CLASS_PATTERN = Pattern.compile(
        "class=\"[^\"]*(?:language-|lang-|brush:\\s*|highlight-source-)([a-zA-Z0-9+#]+)",
        Pattern.CASE_INSENSITIVE
    );

    // VS Code specific meta tag pattern
    private static final Pattern VS_CODE_META_PATTERN = Pattern.compile(
        "<meta[^>]*name=\"generator\"[^>]*content=\"[^\"]*(?:vscode|Visual Studio Code)[^\"]*\"",
        Pattern.CASE_INSENSITIVE
    );

    // HTML tag stripping pattern
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    
    // HTML entity patterns
    private static final Pattern HTML_ENTITY_PATTERN = Pattern.compile("&([a-zA-Z]+|#[0-9]+|#x[0-9a-fA-F]+);");

    /**
     * Checks if the HTML content appears to be code from an IDE.
     * 
     * @param html The HTML content from clipboard
     * @return true if it looks like code, false otherwise
     */
    public static boolean isCodeHtml(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }

        // Check for VS Code meta tag
        if (VS_CODE_META_PATTERN.matcher(html).find()) {
            return true;
        }

        // Check for <pre> or <code> tags
        if (PRE_CODE_PATTERN.matcher(html).find() || CODE_PATTERN.matcher(html).find()) {
            return true;
        }

        // Check for monospace font family (strong indicator of code)
        if (MONOSPACE_FONT_PATTERN.matcher(html).find()) {
            return true;
        }

        // Check for multiple colored spans (syntax highlighting)
        int coloredSpans = countColoredSpans(html);
        if (coloredSpans >= 3) {
            return true;
        }

        return false;
    }

    /**
     * Converts HTML code content to Markdown code block format.
     * 
     * @param html The HTML content from clipboard
     * @return Markdown formatted code block
     */
    public static String convertToMarkdownCodeBlock(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        // Try to detect language
        String language = detectLanguage(html);

        // Extract plain text from HTML
        String plainText = extractPlainText(html);

        // Clean up the text
        plainText = cleanupCode(plainText);

        // Wrap in markdown code block
        if (plainText.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("```").append(language).append("\n");
        sb.append(plainText);
        if (!plainText.endsWith("\n")) {
            sb.append("\n");
        }
        sb.append("```");

        return sb.toString();
    }

    /**
     * Detects the programming language from HTML class names or content.
     */
    private static String detectLanguage(String html) {
        // Try to find language from class attribute
        Matcher matcher = LANGUAGE_CLASS_PATTERN.matcher(html);
        if (matcher.find()) {
            String lang = matcher.group(1).toLowerCase();
            return normalizeLanguage(lang);
        }

        // Try to detect from content patterns
        String content = extractPlainText(html);
        String lowerContent = content.toLowerCase();
        
        // ===== Java (check early - very common) =====
        if (containsJavaKeywords(content)) {
            return "java";
        }
        
        // ===== Go =====
        if (containsGoKeywords(content)) {
            return "go";
        }
        
        // ===== TypeScript (check before JavaScript) =====
        if (containsTypeScriptKeywords(content)) {
            return "typescript";
        }
        
        // ===== JavaScript =====
        if (containsJavaScriptKeywords(content)) {
            return "javascript";
        }
        
        // ===== C# =====
        if (containsCSharpKeywords(content)) {
            return "csharp";
        }
        
        // ===== Python =====
        if (containsPythonKeywords(content)) {
            return "python";
        }
        
        // ===== C / C++ =====
        if (containsCKeywords(content)) {
            // Distinguish C++ from C
            if (containsCppKeywords(content)) {
                return "cpp";
            }
            return "c";
        }
        
        // ===== ABAP (check after other languages - has some common keywords) =====
        if (containsAbapKeywords(content, lowerContent)) {
            return "abap";
        }
        
        // ===== HTML / XML =====
        if (containsHtmlKeywords(content, lowerContent)) {
            return "html";
        }
        
        // XML (not HTML)
        if (lowerContent.contains("<?xml") || lowerContent.contains("xmlns")) {
            return "xml";
        }
        
        // ===== CSS / SCSS =====
        if (containsCssKeywords(content)) {
            if (content.contains("$") && content.contains(":") || content.contains("@mixin") || content.contains("@include")) {
                return "scss";
            }
            return "css";
        }
        
        // ===== SQL =====
        if (containsSqlKeywords(lowerContent)) {
            return "sql";
        }
        
        // ===== PHP =====
        if (content.contains("<?php") || content.contains("<?=") ||
            content.contains("$_GET") || content.contains("$_POST") ||
            content.contains("$this->")) {
            return "php";
        }
        
        // ===== Ruby =====
        if (content.contains("def ") && content.contains("end") ||
            content.contains("puts ") || content.contains("attr_accessor") ||
            content.contains(".each do")) {
            return "ruby";
        }
        
        // ===== Rust =====
        if (content.contains("fn main()") || content.contains("let mut ") ||
            content.contains("impl ") || content.contains("pub fn ") ||
            content.contains("println!(")) {
            return "rust";
        }
        
        // ===== Shell / Bash =====
        if (content.contains("#!/bin/bash") || content.contains("#!/bin/sh") ||
            (content.contains("echo ") && content.contains("if ["))) {
            return "bash";
        }
        
        // ===== PowerShell =====
        if (content.contains("Get-") || content.contains("Set-") ||
            content.contains("Write-Host") || content.contains("-eq")) {
            return "powershell";
        }
        
        // ===== YAML =====
        if (content.contains(": ") && content.contains("\n") && 
            !content.contains("{") && !content.contains(";")) {
            return "yaml";
        }
        
        // ===== JSON =====
        if ((content.trim().startsWith("{") && content.trim().endsWith("}")) ||
            (content.trim().startsWith("[") && content.trim().endsWith("]"))) {
            if (content.contains("\":")) {
                return "json";
            }
        }

        return ""; // No language detected
    }
    
    // ===== ABAP Keywords =====
    private static boolean containsAbapKeywords(String content, String lower) {
        // ABAP-specific keywords that don't appear in other languages
        // Require at least 2 ABAP-specific patterns to avoid false positives
        int abapScore = 0;
        
        // Very specific ABAP keywords (high confidence)
        if (lower.contains("endloop")) abapScore += 3;
        if (lower.contains("endform")) abapScore += 3;
        if (lower.contains("endmethod")) abapScore += 3;
        if (lower.contains("endclass")) abapScore += 3;
        if (lower.contains("endfunction")) abapScore += 3;
        if (lower.contains("endtry")) abapScore += 3;
        if (lower.contains("endif.")) abapScore += 3; // ABAP uses endif. with period
        if (lower.contains("endwhile")) abapScore += 3;
        if (lower.contains("endcase")) abapScore += 3;
        if (lower.contains("endselect")) abapScore += 3;
        if (lower.contains("endat")) abapScore += 3;
        if (lower.contains("enddo")) abapScore += 3;
        
        // ABAP report/program declarations
        if (lower.contains("report ") && !lower.contains("reporter")) abapScore += 3;
        
        // ABAP data declarations with colon
        if (lower.contains("data:") || lower.contains("data :")) abapScore += 3;
        if (lower.contains("types:") || lower.contains("types :")) abapScore += 3;
        if (lower.contains("tables:") || lower.contains("tables :")) abapScore += 3;
        if (lower.contains("parameters:") || lower.contains("parameters :")) abapScore += 3;
        if (lower.contains("constants:") || lower.contains("constants :")) abapScore += 3;
        
        // ABAP internal table operations
        if (lower.contains("loop at ")) abapScore += 3;
        if (lower.contains("read table ")) abapScore += 3;
        if (lower.contains("into table")) abapScore += 3;
        if (lower.contains("into corresponding")) abapScore += 3;
        if (lower.contains("append ") && lower.contains(" to ")) abapScore += 2;
        if (lower.contains("delete adjacent")) abapScore += 3;
        if (lower.contains("sort ") && lower.contains(" by ")) abapScore += 2;
        
        // ABAP SELECT statements
        if (lower.contains("select single")) abapScore += 3;
        if (lower.contains("select * from")) abapScore += 2;
        if (lower.contains("select ") && lower.contains(" into ") && lower.contains(" from ")) abapScore += 2;
        
        // ABAP FORM/PERFORM
        if (lower.contains("form ") && lower.contains("endform")) abapScore += 3;
        if (lower.contains("perform ")) abapScore += 2;
        
        // ABAP function calls
        if (lower.contains("call function")) abapScore += 3;
        if (lower.contains("call method")) abapScore += 3;
        
        // ABAP system variables
        if (lower.contains("sy-subrc")) abapScore += 3;
        if (lower.contains("sy-tabix")) abapScore += 3;
        if (lower.contains("sy-index")) abapScore += 3;
        if (lower.contains("sy-datum")) abapScore += 3;
        if (lower.contains("sy-uzeit")) abapScore += 3;
        if (lower.contains("sy-uname")) abapScore += 3;
        
        // ABAP field symbols
        if (lower.contains("field-symbols")) abapScore += 3;
        if (lower.contains("<fs_")) abapScore += 3;
        if (content.contains("ASSIGN ") || lower.contains(" assign ")) abapScore += 2;
        if (lower.contains("unassign")) abapScore += 3;
        
        // ABAP specific type declarations
        if (lower.contains("type ref to")) abapScore += 3;
        if (lower.contains("type table of")) abapScore += 3;
        if (lower.contains("type standard table")) abapScore += 3;
        if (lower.contains("type sorted table")) abapScore += 3;
        if (lower.contains("type hashed table")) abapScore += 3;
        if (lower.contains("with header line")) abapScore += 3;
        if (lower.contains("begin of") && lower.contains("end of")) abapScore += 3;
        
        // ABAP function/method parameters
        if (lower.contains("exporting") && (lower.contains("importing") || lower.contains("tables"))) abapScore += 3;
        if (lower.contains("changing ") && lower.contains("=")) abapScore += 2;
        if (lower.contains("returning value")) abapScore += 3;
        
        // ABAP class definitions
        if (lower.contains("class ") && lower.contains("definition")) abapScore += 3;
        if (lower.contains("class ") && lower.contains("implementation")) abapScore += 3;
        
        // ABAP events
        if (lower.contains("at selection-screen")) abapScore += 3;
        if (lower.contains("start-of-selection")) abapScore += 3;
        if (lower.contains("end-of-selection")) abapScore += 3;
        if (lower.contains("initialization")) abapScore += 2;
        
        // ABAP string operations
        if (lower.contains("concatenate ") && lower.contains(" into ")) abapScore += 3;
        if (lower.contains("split ") && lower.contains(" at ")) abapScore += 3;
        if (lower.contains("condense ")) abapScore += 3;
        if (lower.contains("translate ") && lower.contains(" to ")) abapScore += 2;
        
        // ABAP write statements
        if (lower.contains("write:") || lower.contains("write /") || lower.contains("write:")) abapScore += 3;
        
        // Modern ABAP expressions
        if (lower.contains("new #(")) abapScore += 3;
        if (lower.contains("value #(")) abapScore += 3;
        if (lower.contains("conv #(")) abapScore += 3;
        if (lower.contains("cond #(")) abapScore += 3;
        if (lower.contains("switch #(")) abapScore += 3;
        if (lower.contains("corresponding #(")) abapScore += 3;
        
        // ABAP predicates
        if (lower.contains("is initial")) abapScore += 2;
        if (lower.contains("is not initial")) abapScore += 3;
        if (lower.contains("is bound")) abapScore += 3;
        if (lower.contains("is assigned")) abapScore += 3;
        if (lower.contains("line_exists(")) abapScore += 3;
        
        // ABAP transaction/database
        if (lower.contains("commit work")) abapScore += 3;
        if (lower.contains("rollback work")) abapScore += 3;
        if (lower.contains("authority-check")) abapScore += 3;
        
        // ABAP message statement
        if (lower.contains("message ") && lower.contains(" type ")) abapScore += 2;
        
        // ABAP-specific functions
        if (lower.contains("lines(")) abapScore += 2;
        if (lower.contains("strlen(") && !content.contains("strlen(const")) abapScore += 1; // C has strlen too
        if (lower.contains("xstrlen(")) abapScore += 3;
        if (lower.contains("boolc(")) abapScore += 3;
        if (lower.contains("xsdbool(")) abapScore += 3;
        
        // Require significant ABAP score to avoid false positives
        return abapScore >= 5;
    }
    
    // ===== HTML Keywords =====
    private static boolean containsHtmlKeywords(String content, String lower) {
        return lower.contains("<!doctype html") ||
               (lower.contains("<html") && lower.contains("</html>")) ||
               (lower.contains("<head>") || lower.contains("<head ")) ||
               (lower.contains("<body>") || lower.contains("<body ")) ||
               (lower.contains("<div") && lower.contains("</div>")) ||
               (lower.contains("<span") && lower.contains("</span>")) ||
               (lower.contains("<p>") || lower.contains("<p ")) ||
               (lower.contains("<h1") || lower.contains("<h2") || lower.contains("<h3")) ||
               (lower.contains("<ul") || lower.contains("<ol") || lower.contains("<li")) ||
               (lower.contains("<table") || lower.contains("<tr") || lower.contains("<td")) ||
               (lower.contains("<form") && lower.contains("</form>")) ||
               (lower.contains("<input") || lower.contains("<button") || lower.contains("<select")) ||
               (lower.contains("<a ") && lower.contains("href=")) ||
               (lower.contains("<img") && lower.contains("src=")) ||
               (lower.contains("<link") && lower.contains("rel=")) ||
               (lower.contains("<meta") && (lower.contains("charset") || lower.contains("content="))) ||
               (lower.contains("<script") && lower.contains("</script>")) ||
               (lower.contains("<style") && lower.contains("</style>")) ||
               (lower.contains("<nav") || lower.contains("<header") || lower.contains("<footer")) ||
               (lower.contains("<section") || lower.contains("<article") || lower.contains("<aside")) ||
               (lower.contains("<main") || lower.contains("<figure") || lower.contains("<figcaption")) ||
               lower.contains("<br") || lower.contains("<hr") ||
               lower.contains("<strong") || lower.contains("<em") || lower.contains("<b>") ||
               lower.contains("<iframe") || lower.contains("<video") || lower.contains("<audio") ||
               lower.contains("<canvas") || lower.contains("<svg") ||
               lower.contains("class=\"") || lower.contains("id=\"") || lower.contains("style=\"");
    }
    
    // ===== Go Keywords =====
    private static boolean containsGoKeywords(String content) {
        return content.contains("package main") ||
               content.contains("package ") && content.contains("import") ||
               content.contains("func ") && content.contains(") {") ||
               content.contains("func main()") ||
               content.contains(":= ") ||
               content.contains("go func") ||
               content.contains("interface{}") ||
               content.contains("struct {") ||
               content.contains("*sql.DB") || content.contains("*http.") ||
               content.contains("http.Handle") || content.contains("http.ListenAndServe") ||
               content.contains("fmt.Print") || content.contains("fmt.Sprintf") ||
               content.contains("log.Print") || content.contains("log.Fatal") ||
               content.contains("err != nil") || content.contains("err == nil") ||
               content.contains("if err != nil") ||
               content.contains("defer ") ||
               content.contains("go ") && content.contains("chan ") ||
               content.contains("make(") && (content.contains("[]") || content.contains("map[") || content.contains("chan ")) ||
               content.contains("append(") ||
               content.contains("range ") ||
               content.contains("select {") ||
               content.contains("case <-") ||
               content.contains("fallthrough") ||
               content.contains("type ") && content.contains(" struct") ||
               content.contains("type ") && content.contains(" interface") ||
               content.contains("json.Marshal") || content.contains("json.Unmarshal") ||
               content.contains("ioutil.") || content.contains("os.") ||
               content.contains("filepath.") || content.contains("strings.") ||
               content.contains("strconv.") || content.contains("time.") ||
               content.contains("context.") || content.contains("sync.") ||
               content.contains("var ") && content.contains(" = ") && !content.contains(";") ||
               content.contains("const (") ||
               content.contains("nil") && content.contains("func ");
    }
    
    // ===== Java Keywords =====
    private static boolean containsJavaKeywords(String content) {
        return content.contains("public class ") ||
               content.contains("private class ") ||
               content.contains("protected class ") ||
               content.contains("public interface ") ||
               content.contains("public enum ") ||
               content.contains("public abstract ") ||
               content.contains("private void ") ||
               content.contains("public void ") ||
               content.contains("protected void ") ||
               content.contains("private static ") ||
               content.contains("public static ") ||
               content.contains("public static void main") ||
               content.contains("import java.") ||
               content.contains("import javax.") ||
               content.contains("import org.") && content.contains(";") ||
               content.contains("@Override") ||
               content.contains("@Autowired") ||
               content.contains("@Component") ||
               content.contains("@Service") ||
               content.contains("@Repository") ||
               content.contains("@Controller") ||
               content.contains("@RestController") ||
               content.contains("@RequestMapping") ||
               content.contains("@GetMapping") || content.contains("@PostMapping") ||
               content.contains("@Entity") || content.contains("@Table") ||
               content.contains("@Id") || content.contains("@Column") ||
               content.contains("@Test") || content.contains("@Before") || content.contains("@After") ||
               content.contains("System.out.print") ||
               content.contains("System.err.") ||
               content.contains("new ArrayList") || content.contains("new LinkedList") ||
               content.contains("new HashMap") || content.contains("new HashSet") ||
               content.contains("new StringBuilder") || content.contains("new StringBuffer") ||
               content.contains("extends ") && content.contains("{") ||
               content.contains("implements ") && content.contains("{") ||
               content.contains("throws ") ||
               content.contains("try {") || content.contains("catch (") || content.contains("finally {") ||
               content.contains("synchronized ") ||
               content.contains("volatile ") ||
               content.contains("transient ") ||
               content.contains("instanceof ") ||
               content.contains(".stream()") || content.contains(".collect(") ||
               content.contains(".forEach(") || content.contains(".map(") ||
               content.contains("Optional.") ||
               content.contains("CompletableFuture") ||
               content.contains("Logger.") || content.contains("LoggerFactory.");
    }
    
    // ===== TypeScript Keywords =====
    private static boolean containsTypeScriptKeywords(String content) {
        return content.contains(": string") ||
               content.contains(": number") ||
               content.contains(": boolean") ||
               content.contains(": any") ||
               content.contains(": void") ||
               content.contains(": null") ||
               content.contains(": undefined") ||
               content.contains(": never") ||
               content.contains(": unknown") ||
               content.contains(": object") ||
               content.contains("string[]") || content.contains("number[]") || content.contains("boolean[]") ||
               content.contains("Array<") ||
               content.contains("interface ") && content.contains("{") ||
               content.contains("type ") && content.contains(" = ") && content.contains(";") ||
               content.contains("<T>") || content.contains("<T,") || content.contains("<T extends") ||
               content.contains("as string") || content.contains("as number") || content.contains("as any") ||
               content.contains(": Promise<") ||
               content.contains(": Observable<") ||
               content.contains("readonly ") ||
               content.contains("private ") && content.contains(": ") ||
               content.contains("public ") && content.contains(": ") ||
               content.contains("protected ") && content.contains(": ") ||
               content.contains("constructor(") && content.contains(": ") ||
               content.contains("implements ") ||
               content.contains("extends ") && content.contains("<") ||
               content.contains("keyof ") ||
               content.contains("typeof ") && content.contains(": ") ||
               content.contains("Partial<") || content.contains("Required<") || content.contains("Pick<") ||
               content.contains("Omit<") || content.contains("Record<") || content.contains("Exclude<") ||
               content.contains("Extract<") || content.contains("NonNullable<") ||
               content.contains("ReturnType<") || content.contains("Parameters<") ||
               content.contains("enum ") && content.contains("{") ||
               content.contains("namespace ") && content.contains("{") ||
               content.contains("declare ") ||
               content.contains("abstract class ") ||
               content.contains("@Injectable") || content.contains("@Component") ||
               content.contains("@Input") || content.contains("@Output");
    }
    
    // ===== JavaScript Keywords =====
    private static boolean containsJavaScriptKeywords(String content) {
        return content.contains("function ") && content.contains("(") && content.contains(") {") ||
               content.contains("function(") ||
               content.contains("const ") && content.contains(" = ") ||
               content.contains("let ") && content.contains(" = ") ||
               content.contains("var ") && content.contains(" = ") ||
               content.contains("=> {") || content.contains("=> ") ||
               content.contains("async ") || content.contains("await ") ||
               content.contains("console.log") || content.contains("console.error") || content.contains("console.warn") ||
               content.contains("document.") ||
               content.contains("window.") ||
               content.contains("addEventListener") || content.contains("removeEventListener") ||
               content.contains("getElementById") || content.contains("querySelector") ||
               content.contains("createElement") || content.contains("appendChild") ||
               content.contains("innerHTML") || content.contains("textContent") ||
               content.contains("className") || content.contains("classList") ||
               content.contains("setAttribute") || content.contains("getAttribute") ||
               content.contains("require(") ||
               content.contains("module.exports") ||
               content.contains("export default") ||
               content.contains("export const") || content.contains("export function") ||
               content.contains("export class") ||
               content.contains("import ") && content.contains(" from ") ||
               content.contains("import {") ||
               content.contains("new Promise") ||
               content.contains(".then(") || content.contains(".catch(") || content.contains(".finally(") ||
               content.contains("fetch(") ||
               content.contains("JSON.parse") || content.contains("JSON.stringify") ||
               content.contains("Array.") || content.contains("Object.") || content.contains("String.") ||
               content.contains(".map(") || content.contains(".filter(") || content.contains(".reduce(") ||
               content.contains(".forEach(") || content.contains(".find(") || content.contains(".some(") ||
               content.contains(".every(") || content.contains(".includes(") ||
               content.contains("setTimeout") || content.contains("setInterval") ||
               content.contains("localStorage") || content.contains("sessionStorage") ||
               content.contains("try {") || content.contains("catch (") ||
               content.contains("throw new Error") ||
               content.contains("typeof ") || content.contains("instanceof ") ||
               content.contains("null") || content.contains("undefined") ||
               content.contains("true") || content.contains("false") ||
               content.contains("this.") && !content.contains("$this->") ||
               content.contains("class ") && content.contains("constructor(") ||
               content.contains("extends ") && content.contains("super(") ||
               content.contains("get ") && content.contains("() {") ||
               content.contains("set ") && content.contains("(") && content.contains(") {") ||
               content.contains("static ") && content.contains("() {");
    }
    
    // ===== Python Keywords =====
    private static boolean containsPythonKeywords(String content) {
        return content.contains("def ") && content.contains("):") ||
               content.contains("async def ") ||
               content.contains("class ") && content.contains("):") ||
               content.contains("import ") && !content.contains("import {") && !content.contains("import java") ||
               content.contains("from ") && content.contains(" import ") ||
               content.contains("if __name__") ||
               content.contains("self.") ||
               content.contains("self,") ||
               content.contains("cls.") || content.contains("cls,") ||
               content.contains("print(") ||
               content.contains("elif ") ||
               content.contains("except ") || content.contains("except:") ||
               content.contains("raise ") ||
               content.contains("finally:") ||
               content.contains("with ") && content.contains(" as ") ||
               content.contains("lambda ") ||
               content.contains("yield ") ||
               content.contains("await ") ||
               content.contains("pass") ||
               content.contains("continue") ||
               content.contains("break") ||
               content.contains("return ") ||
               content.contains("global ") ||
               content.contains("nonlocal ") ||
               content.contains("assert ") ||
               content.contains("del ") ||
               content.contains("True") || content.contains("False") || content.contains("None") ||
               content.contains("and ") || content.contains(" or ") || content.contains("not ") ||
               content.contains(" in ") && content.contains(":") ||
               content.contains("for ") && content.contains(" in ") && content.contains(":") ||
               content.contains("while ") && content.contains(":") ||
               content.contains("if ") && content.contains(":") && !content.contains("{") ||
               content.contains("else:") ||
               content.contains("@property") || content.contains("@staticmethod") || content.contains("@classmethod") ||
               content.contains("@abstractmethod") ||
               content.contains("__init__") || content.contains("__str__") || content.contains("__repr__") ||
               content.contains("__name__") || content.contains("__main__") ||
               content.contains("__class__") || content.contains("__dict__") ||
               content.contains("len(") || content.contains("range(") || content.contains("enumerate(") ||
               content.contains("list(") || content.contains("dict(") || content.contains("set(") || content.contains("tuple(") ||
               content.contains("str(") || content.contains("int(") || content.contains("float(") ||
               content.contains("open(") || content.contains("read(") || content.contains("write(") ||
               content.contains(".append(") || content.contains(".extend(") || content.contains(".pop(") ||
               content.contains(".keys()") || content.contains(".values()") || content.contains(".items()") ||
               content.contains(".join(") || content.contains(".split(") || content.contains(".strip()") ||
               content.contains(".format(") || content.contains("f\"") || content.contains("f'") ||
               content.contains("try:") || content.contains("except ") ||
               content.contains("# ") && content.contains("\n");
    }
    
    // ===== C Keywords =====
    private static boolean containsCKeywords(String content) {
        return content.contains("#include <") || content.contains("#include \"") ||
               content.contains("#define ") ||
               content.contains("#ifdef ") || content.contains("#ifndef ") || content.contains("#endif") ||
               content.contains("#pragma ") ||
               content.contains("int main(") || content.contains("void main(") ||
               content.contains("int argc") || content.contains("char *argv") || content.contains("char **argv") ||
               content.contains("printf(") || content.contains("fprintf(") || content.contains("sprintf(") ||
               content.contains("scanf(") || content.contains("fscanf(") || content.contains("sscanf(") ||
               content.contains("malloc(") || content.contains("calloc(") || content.contains("realloc(") ||
               content.contains("free(") ||
               content.contains("sizeof(") ||
               content.contains("NULL") ||
               content.contains("struct ") && content.contains("{") ||
               content.contains("union ") && content.contains("{") ||
               content.contains("enum ") && content.contains("{") ||
               content.contains("typedef ") ||
               content.contains("static ") && content.contains("(") ||
               content.contains("extern ") ||
               content.contains("const ") && content.contains("*") ||
               content.contains("volatile ") ||
               content.contains("unsigned ") || content.contains("signed ") ||
               content.contains("short ") || content.contains("long ") ||
               content.contains("char ") || content.contains("int ") || content.contains("float ") || content.contains("double ") ||
               content.contains("void ") && content.contains("(") ||
               content.contains("return ") && content.contains(";") ||
               content.contains("if (") || content.contains("else if (") || content.contains("else {") ||
               content.contains("for (") || content.contains("while (") || content.contains("do {") ||
               content.contains("switch (") || content.contains("case ") && content.contains(":") ||
               content.contains("break;") || content.contains("continue;") ||
               content.contains("goto ") ||
               content.contains("->") || content.contains(".*") ||
               content.contains("&") && content.contains("*") ||
               content.contains("fopen(") || content.contains("fclose(") || content.contains("fread(") || content.contains("fwrite(") ||
               content.contains("memcpy(") || content.contains("memset(") || content.contains("memmove(") ||
               content.contains("strcpy(") || content.contains("strcat(") || content.contains("strlen(") || content.contains("strcmp(") ||
               content.contains("atoi(") || content.contains("atof(") ||
               content.contains("exit(") || content.contains("abort(");
    }
    
    // ===== C++ Keywords (additional to C) =====
    private static boolean containsCppKeywords(String content) {
        return content.contains("cout <<") || content.contains("cout<<") ||
               content.contains("cin >>") || content.contains("cin>>") ||
               content.contains("cerr <<") || content.contains("endl") ||
               content.contains("std::") ||
               content.contains("using namespace") ||
               content.contains("class ") && content.contains("public:") ||
               content.contains("class ") && content.contains("private:") ||
               content.contains("class ") && content.contains("protected:") ||
               content.contains("template<") || content.contains("template <") ||
               content.contains("typename ") ||
               content.contains("nullptr") ||
               content.contains("new ") && content.contains(";") ||
               content.contains("delete ") || content.contains("delete[] ") ||
               content.contains("virtual ") ||
               content.contains("override") ||
               content.contains("final") && content.contains("class ") ||
               content.contains("constexpr ") ||
               content.contains("auto ") && content.contains(" = ") ||
               content.contains("decltype(") ||
               content.contains("noexcept") ||
               content.contains("explicit ") ||
               content.contains("friend ") ||
               content.contains("mutable ") ||
               content.contains("operator") && content.contains("(") ||
               content.contains("dynamic_cast<") || content.contains("static_cast<") ||
               content.contains("reinterpret_cast<") || content.contains("const_cast<") ||
               content.contains("try {") && content.contains("catch (") ||
               content.contains("throw ") ||
               content.contains("namespace ") && content.contains("{") ||
               content.contains("::") && content.contains("(") ||
               content.contains("vector<") || content.contains("string ") ||
               content.contains("map<") || content.contains("set<") ||
               content.contains("list<") || content.contains("deque<") ||
               content.contains("unique_ptr<") || content.contains("shared_ptr<") || content.contains("weak_ptr<") ||
               content.contains("make_unique<") || content.contains("make_shared<") ||
               content.contains("std::string") || content.contains("std::vector") ||
               content.contains("std::cout") || content.contains("std::cin") ||
               content.contains("std::endl") || content.contains("std::move") ||
               content.contains("std::function") || content.contains("std::thread") ||
               content.contains("std::mutex") || content.contains("std::lock_guard") ||
               content.contains("std::async") || content.contains("std::future") ||
               content.contains("std::optional") || content.contains("std::variant") ||
               content.contains("std::any") || content.contains("std::tuple") ||
               content.contains("std::pair") || content.contains("std::array") ||
               content.contains("std::begin(") || content.contains("std::end(") ||
               content.contains("std::sort(") || content.contains("std::find(") ||
               content.contains("std::transform(") || content.contains("std::for_each(") ||
               content.contains("std::accumulate(") ||
               content.contains("std::chrono::") || content.contains("std::filesystem::");
    }
    
    // ===== C# Keywords =====
    private static boolean containsCSharpKeywords(String content) {
        return content.contains("using System") ||
               content.contains("using Microsoft.") ||
               content.contains("using UnityEngine") ||
               content.contains("namespace ") && content.contains("{") && !content.contains("<?php") ||
               content.contains("public class ") && content.contains(";") ||
               content.contains("private class ") ||
               content.contains("protected class ") ||
               content.contains("internal class ") ||
               content.contains("public interface ") ||
               content.contains("public enum ") ||
               content.contains("public struct ") ||
               content.contains("public void ") || content.contains("private void ") ||
               content.contains("public static void ") ||
               content.contains("public async ") || content.contains("private async ") ||
               content.contains("async Task") || content.contains("async void") ||
               content.contains("await ") ||
               content.contains("Console.Write") || content.contains("Console.Read") ||
               content.contains("Debug.Log") ||
               content.contains("[Attribute]") ||
               content.contains("[Serializable]") ||
               content.contains("[SerializeField]") ||
               content.contains("[HttpGet]") || content.contains("[HttpPost]") ||
               content.contains("[Route(") || content.contains("[ApiController]") ||
               content.contains("[TestMethod]") || content.contains("[TestClass]") ||
               content.contains("[Fact]") || content.contains("[Theory]") ||
               content.contains("var ") && content.contains(" = new ") && content.contains(";") ||
               content.contains("string ") && content.contains(" = ") && content.contains(";") ||
               content.contains("int ") && content.contains(" = ") && content.contains(";") ||
               content.contains("bool ") && content.contains(" = ") && content.contains(";") ||
               content.contains("List<") || content.contains("Dictionary<") ||
               content.contains("IEnumerable<") || content.contains("IList<") ||
               content.contains("Action<") || content.contains("Func<") ||
               content.contains("Task<") || content.contains("ValueTask<") ||
               content.contains("get;") || content.contains("set;") ||
               content.contains("{ get; set; }") || content.contains("{ get; }") ||
               content.contains("=> ") && content.contains(";") ||
               content.contains("base.") || content.contains("this.") ||
               content.contains("virtual ") || content.contains("override ") ||
               content.contains("abstract ") || content.contains("sealed ") ||
               content.contains("readonly ") ||
               content.contains("partial class ") ||
               content.contains("static class ") ||
               content.contains("new()") || content.contains("where T :") ||
               content.contains("is ") && content.contains(" pattern") ||
               content.contains("switch ") && content.contains(" => ") ||
               content.contains("nameof(") ||
               content.contains("typeof(") ||
               content.contains("?.") || content.contains("??") || content.contains("?[") ||
               content.contains("throw new ") ||
               content.contains("try {") || content.contains("catch (") || content.contains("finally {") ||
               content.contains("lock (") ||
               content.contains("LINQ") || content.contains(".Where(") || content.contains(".Select(") ||
               content.contains(".OrderBy(") || content.contains(".GroupBy(") ||
               content.contains(".FirstOrDefault(") || content.contains(".ToList()") ||
               content.contains("StringBuilder") || content.contains("StringComparison") ||
               content.contains("DateTime") || content.contains("TimeSpan") ||
               content.contains("Guid.") || content.contains("Path.") ||
               content.contains("File.") || content.contains("Directory.") ||
               content.contains("HttpClient") || content.contains("WebRequest") ||
               content.contains("JsonConvert.") || content.contains("JsonSerializer.");
    }
    
    // ===== CSS Keywords =====
    private static boolean containsCssKeywords(String content) {
        return content.contains("{") && content.contains("}") &&
               (content.contains("color:") || content.contains("color :") ||
                content.contains("background:") || content.contains("background-color:") ||
                content.contains("background-image:") || content.contains("background-size:") ||
                content.contains("margin:") || content.contains("margin-top:") || content.contains("margin-bottom:") ||
                content.contains("margin-left:") || content.contains("margin-right:") ||
                content.contains("padding:") || content.contains("padding-top:") || content.contains("padding-bottom:") ||
                content.contains("padding-left:") || content.contains("padding-right:") ||
                content.contains("display:") || content.contains("position:") ||
                content.contains("top:") || content.contains("bottom:") || content.contains("left:") || content.contains("right:") ||
                content.contains("width:") || content.contains("height:") ||
                content.contains("min-width:") || content.contains("max-width:") ||
                content.contains("min-height:") || content.contains("max-height:") ||
                content.contains("font-family:") || content.contains("font-size:") ||
                content.contains("font-weight:") || content.contains("font-style:") ||
                content.contains("text-align:") || content.contains("text-decoration:") ||
                content.contains("line-height:") || content.contains("letter-spacing:") ||
                content.contains("border:") || content.contains("border-radius:") ||
                content.contains("border-top:") || content.contains("border-bottom:") ||
                content.contains("border-left:") || content.contains("border-right:") ||
                content.contains("border-width:") || content.contains("border-style:") || content.contains("border-color:") ||
                content.contains("box-shadow:") || content.contains("text-shadow:") ||
                content.contains("opacity:") || content.contains("visibility:") ||
                content.contains("overflow:") || content.contains("overflow-x:") || content.contains("overflow-y:") ||
                content.contains("z-index:") ||
                content.contains("flex:") || content.contains("flex-direction:") || content.contains("flex-wrap:") ||
                content.contains("justify-content:") || content.contains("align-items:") || content.contains("align-content:") ||
                content.contains("grid:") || content.contains("grid-template:") ||
                content.contains("grid-template-columns:") || content.contains("grid-template-rows:") ||
                content.contains("gap:") || content.contains("grid-gap:") ||
                content.contains("transform:") || content.contains("transition:") ||
                content.contains("animation:") || content.contains("animation-name:") ||
                content.contains("cursor:") ||
                content.contains(":hover") || content.contains(":focus") || content.contains(":active") ||
                content.contains(":first-child") || content.contains(":last-child") || content.contains(":nth-child") ||
                content.contains("::before") || content.contains("::after") ||
                content.contains("@media") || content.contains("@keyframes") ||
                content.contains("@import") || content.contains("@font-face") ||
                content.contains("!important") ||
                content.contains("rgb(") || content.contains("rgba(") ||
                content.contains("hsl(") || content.contains("hsla(") ||
                content.contains("#") && content.matches(".*#[0-9a-fA-F]{3,8}.*") ||
                content.contains("px") || content.contains("em") || content.contains("rem") ||
                content.contains("vh") || content.contains("vw") || content.contains("%"));
    }
    
    // ===== SQL Keywords =====
    private static boolean containsSqlKeywords(String lower) {
        return lower.contains("select ") && lower.contains(" from ") ||
               lower.contains("select *") ||
               lower.contains("insert into ") ||
               lower.contains("update ") && lower.contains(" set ") ||
               lower.contains("delete from ") ||
               lower.contains("create table ") ||
               lower.contains("create index ") ||
               lower.contains("create view ") ||
               lower.contains("create procedure ") ||
               lower.contains("create function ") ||
               lower.contains("create trigger ") ||
               lower.contains("create database ") ||
               lower.contains("create schema ") ||
               lower.contains("alter table ") ||
               lower.contains("alter column ") ||
               lower.contains("drop table ") ||
               lower.contains("drop index ") ||
               lower.contains("drop view ") ||
               lower.contains("drop database ") ||
               lower.contains("truncate table ") ||
               lower.contains("where ") ||
               lower.contains(" and ") && (lower.contains("where") || lower.contains("on ")) ||
               lower.contains(" or ") && lower.contains("where") ||
               lower.contains(" not ") && lower.contains("where") ||
               lower.contains(" in (") || lower.contains(" not in (") ||
               lower.contains(" between ") ||
               lower.contains(" like ") ||
               lower.contains(" is null") || lower.contains(" is not null") ||
               lower.contains("order by ") ||
               lower.contains("group by ") ||
               lower.contains("having ") ||
               lower.contains(" asc") || lower.contains(" desc") ||
               lower.contains("inner join ") ||
               lower.contains("left join ") || lower.contains("left outer join ") ||
               lower.contains("right join ") || lower.contains("right outer join ") ||
               lower.contains("full join ") || lower.contains("full outer join ") ||
               lower.contains("cross join ") ||
               lower.contains(" on ") && lower.contains(" = ") ||
               lower.contains("union ") || lower.contains("union all ") ||
               lower.contains("intersect ") || lower.contains("except ") ||
               lower.contains("distinct ") ||
               lower.contains("limit ") || lower.contains("offset ") ||
               lower.contains("top ") ||
               lower.contains("count(") || lower.contains("sum(") || lower.contains("avg(") ||
               lower.contains("min(") || lower.contains("max(") ||
               lower.contains("coalesce(") || lower.contains("nullif(") ||
               lower.contains("case when ") || lower.contains(" then ") || lower.contains(" else ") || lower.contains(" end") ||
               lower.contains("cast(") || lower.contains("convert(") ||
               lower.contains("varchar") || lower.contains("nvarchar") ||
               lower.contains("integer") || lower.contains("bigint") || lower.contains("smallint") ||
               lower.contains("decimal") || lower.contains("numeric") ||
               lower.contains("datetime") || lower.contains("timestamp") ||
               lower.contains("primary key") || lower.contains("foreign key") ||
               lower.contains("references ") ||
               lower.contains("unique") || lower.contains("not null") ||
               lower.contains("default ") ||
               lower.contains("auto_increment") || lower.contains("identity") ||
               lower.contains("constraint ") ||
               lower.contains("begin ") || lower.contains("commit") || lower.contains("rollback") ||
               lower.contains("transaction") ||
               lower.contains("exec ") || lower.contains("execute ") ||
               lower.contains("declare ") || lower.contains("@") && lower.contains(" = ") ||
               lower.contains("cursor ") ||
               lower.contains("fetch ") ||
               lower.contains("open ") && lower.contains("cursor") ||
               lower.contains("close ") && lower.contains("cursor");
    }

    /**
     * Normalizes language names to common markdown code fence identifiers.
     */
    private static String normalizeLanguage(String lang) {
        switch (lang) {
            case "js":
                return "javascript";
            case "ts":
                return "typescript";
            case "py":
                return "python";
            case "rb":
                return "ruby";
            case "cs":
            case "csharp":
                return "csharp";
            case "cpp":
            case "c++":
                return "cpp";
            case "sh":
            case "shell":
                return "bash";
            case "yml":
                return "yaml";
            case "md":
                return "markdown";
            case "ps1":
            case "powershell":
                return "powershell";
            default:
                return lang;
        }
    }

    /**
     * Extracts plain text from HTML, preserving line breaks.
     */
    private static String extractPlainText(String html) {
        // Remove <style> blocks completely (CSS definitions should not appear in code)
        String text = html.replaceAll("(?is)<style[^>]*>.*?</style>", "");
        
        // Remove <script> blocks completely
        text = text.replaceAll("(?is)<script[^>]*>.*?</script>", "");
        
        // Remove <head> blocks completely (contains meta, style, etc.)
        text = text.replaceAll("(?is)<head[^>]*>.*?</head>", "");
        
        // Replace <br>, </div>, </p>, </li> with newlines
        text = text.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replaceAll("(?i)</div>", "\n");
        text = text.replaceAll("(?i)</p>", "\n");
        text = text.replaceAll("(?i)</li>", "\n");
        text = text.replaceAll("(?i)</tr>", "\n");
        
        // Remove all HTML tags
        text = HTML_TAG_PATTERN.matcher(text).replaceAll("");
        
        // Decode HTML entities
        text = decodeHtmlEntities(text);
        
        return text;
    }

    /**
     * Decodes common HTML entities.
     */
    private static String decodeHtmlEntities(String text) {
        text = text.replace("&nbsp;", " ");
        text = text.replace("&lt;", "<");
        text = text.replace("&gt;", ">");
        text = text.replace("&amp;", "&");
        text = text.replace("&quot;", "\"");
        text = text.replace("&apos;", "'");
        text = text.replace("&#39;", "'");
        text = text.replace("&#x27;", "'");
        text = text.replace("&tab;", "\t");
        text = text.replace("&#9;", "\t");
        text = text.replace("&#10;", "\n");
        text = text.replace("&#13;", "\r");
        
        // Handle numeric entities
        Matcher matcher = Pattern.compile("&#(\\d+);").matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            int code = Integer.parseInt(matcher.group(1));
            matcher.appendReplacement(sb, String.valueOf((char) code));
        }
        matcher.appendTail(sb);
        text = sb.toString();
        
        // Handle hex entities
        matcher = Pattern.compile("&#x([0-9a-fA-F]+);").matcher(text);
        sb = new StringBuffer();
        while (matcher.find()) {
            int code = Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(sb, String.valueOf((char) code));
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }

    /**
     * Cleans up extracted code text.
     */
    private static String cleanupCode(String text) {
        // Remove Windows line endings
        text = text.replace("\r\n", "\n");
        text = text.replace("\r", "\n");
        
        // Remove multiple consecutive blank lines
        text = text.replaceAll("\n{3,}", "\n\n");
        
        // Trim leading/trailing whitespace but preserve internal indentation
        String[] lines = text.split("\n", -1);
        
        // Find minimum indentation (excluding empty lines)
        int minIndent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                int indent = 0;
                for (char c : line.toCharArray()) {
                    if (c == ' ') indent++;
                    else if (c == '\t') indent += 4;
                    else break;
                }
                minIndent = Math.min(minIndent, indent);
            }
        }
        
        // Don't remove indentation if it's minimal
        if (minIndent <= 0 || minIndent == Integer.MAX_VALUE) {
            return text.trim();
        }
        
        // Remove common leading whitespace
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.trim().isEmpty() && line.length() > minIndent) {
                // Remove minimum indentation
                int removed = 0;
                int j = 0;
                while (j < line.length() && removed < minIndent) {
                    char c = line.charAt(j);
                    if (c == ' ') {
                        removed++;
                        j++;
                    } else if (c == '\t') {
                        removed += 4;
                        j++;
                    } else {
                        break;
                    }
                }
                line = line.substring(j);
            }
            sb.append(line);
            if (i < lines.length - 1) {
                sb.append("\n");
            }
        }
        
        return sb.toString().trim();
    }

    /**
     * Counts the number of spans with color styling (indicator of syntax highlighting).
     */
    private static int countColoredSpans(String html) {
        Pattern colorPattern = Pattern.compile(
            "<span[^>]*style=\"[^\"]*color\\s*:",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = colorPattern.matcher(html);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
