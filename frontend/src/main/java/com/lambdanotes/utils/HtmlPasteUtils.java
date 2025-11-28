package com.lambdanotes.utils;

import java.util.HashMap;
import java.util.Map;
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

        // Try to detect from content patterns using a scoring system
        String content = extractPlainText(html);
        String lowerContent = content.toLowerCase();
        
        Map<String, Integer> scores = new HashMap<>();
        scores.put("java", calculateJavaScore(content));
        scores.put("go", calculateGoScore(content));
        scores.put("typescript", calculateTypeScriptScore(content));
        scores.put("javascript", calculateJavaScriptScore(content));
        scores.put("csharp", calculateCSharpScore(content));
        scores.put("python", calculatePythonScore(content));
        scores.put("c", calculateCScore(content));
        scores.put("cpp", calculateCppScore(content));
        scores.put("abap", calculateAbapScore(content, lowerContent));
        scores.put("html", calculateHtmlScore(content, lowerContent));
        scores.put("css", calculateCssScore(content));
        scores.put("sql", calculateSqlScore(lowerContent));
        scores.put("xml", calculateXmlScore(lowerContent));
        scores.put("php", calculatePhpScore(content));
        scores.put("ruby", calculateRubyScore(content));
        scores.put("rust", calculateRustScore(content));
        scores.put("bash", calculateBashScore(content));
        scores.put("powershell", calculatePowerShellScore(content));
        scores.put("yaml", calculateYamlScore(content));
        scores.put("json", calculateJsonScore(content));

        // Find the language with the highest score
        String bestLang = "";
        int maxScore = 0;
        
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > maxScore) {
                maxScore = entry.getValue();
                bestLang = entry.getKey();
            }
        }
        
        // Require a minimum score to avoid false positives
        if (maxScore >= 2) {
            return bestLang;
        }

        return ""; // No language detected
    }
    
    // ===== ABAP Keywords =====
    private static int calculateAbapScore(String content, String lower) {
        // ABAP-specific keywords that don't appear in other languages
        // Require at least 2 ABAP-specific patterns to avoid false positives
        int score = 0;
        
        // Very specific ABAP keywords (high confidence)
        if (lower.contains("endloop")) score += 3;
        if (lower.contains("endform")) score += 3;
        if (lower.contains("endmethod")) score += 3;
        if (lower.contains("endclass")) score += 3;
        if (lower.contains("endfunction")) score += 3;
        if (lower.contains("endtry")) score += 3;
        if (lower.contains("endif.")) score += 3; // ABAP uses endif. with period
        if (lower.contains("endwhile")) score += 3;
        if (lower.contains("endcase")) score += 3;
        if (lower.contains("endselect")) score += 3;
        if (lower.contains("endat")) score += 3;
        if (lower.contains("enddo")) score += 3;
        
        // ABAP report/program declarations
        if (lower.contains("report ") && !lower.contains("reporter")) score += 3;
        
        // ABAP data declarations with colon
        if (lower.contains("data:") || lower.contains("data :")) score += 3;
        if (lower.contains("types:") || lower.contains("types :")) score += 3;
        if (lower.contains("tables:") || lower.contains("tables :")) score += 3;
        if (lower.contains("parameters:") || lower.contains("parameters :")) score += 3;
        if (lower.contains("constants:") || lower.contains("constants :")) score += 3;
        
        // ABAP internal table operations
        if (lower.contains("loop at ")) score += 3;
        if (lower.contains("read table ")) score += 3;
        if (lower.contains("into table")) score += 3;
        if (lower.contains("into corresponding")) score += 3;
        if (lower.contains("append ") && lower.contains(" to ")) score += 2;
        if (lower.contains("delete adjacent")) score += 3;
        if (lower.contains("sort ") && lower.contains(" by ")) score += 2;
        
        // ABAP SELECT statements
        if (lower.contains("select single")) score += 3;
        if (lower.contains("select * from")) score += 2;
        if (lower.contains("select ") && lower.contains(" into ") && lower.contains(" from ")) score += 2;
        
        // ABAP FORM/PERFORM
        if (lower.contains("form ") && lower.contains("endform")) score += 3;
        if (lower.contains("perform ")) score += 2;
        
        // ABAP function calls
        if (lower.contains("call function")) score += 3;
        if (lower.contains("call method")) score += 3;
        
        // ABAP system variables
        if (lower.contains("sy-subrc")) score += 3;
        if (lower.contains("sy-tabix")) score += 3;
        if (lower.contains("sy-index")) score += 3;
        if (lower.contains("sy-datum")) score += 3;
        if (lower.contains("sy-uzeit")) score += 3;
        if (lower.contains("sy-uname")) score += 3;
        
        // ABAP field symbols
        if (lower.contains("field-symbols")) score += 3;
        if (lower.contains("<fs_")) score += 3;
        if (content.contains("ASSIGN ") || lower.contains(" assign ")) score += 2;
        if (lower.contains("unassign")) score += 3;
        
        // ABAP specific type declarations
        if (lower.contains("type ref to")) score += 3;
        if (lower.contains("type table of")) score += 3;
        if (lower.contains("type standard table")) score += 3;
        if (lower.contains("type sorted table")) score += 3;
        if (lower.contains("type hashed table")) score += 3;
        if (lower.contains("with header line")) score += 3;
        if (lower.contains("begin of") && lower.contains("end of")) score += 3;
        
        // ABAP function/method parameters
        if (lower.contains("exporting") && (lower.contains("importing") || lower.contains("tables"))) score += 3;
        if (lower.contains("changing ") && lower.contains("=")) score += 2;
        if (lower.contains("returning value")) score += 3;
        
        // ABAP class definitions
        if (lower.contains("class ") && lower.contains("definition")) score += 3;
        if (lower.contains("class ") && lower.contains("implementation")) score += 3;
        
        // ABAP events
        if (lower.contains("at selection-screen")) score += 3;
        if (lower.contains("start-of-selection")) score += 3;
        if (lower.contains("end-of-selection")) score += 3;
        if (lower.contains("initialization")) score += 2;
        
        // ABAP string operations
        if (lower.contains("concatenate ") && lower.contains(" into ")) score += 3;
        if (lower.contains("split ") && lower.contains(" at ")) score += 3;
        if (lower.contains("condense ")) score += 3;
        if (lower.contains("translate ") && lower.contains(" to ")) score += 2;
        
        // ABAP write statements
        if (lower.contains("write:") || lower.contains("write /") || lower.contains("write:")) score += 3;
        
        // Modern ABAP expressions
        if (lower.contains("new #(")) score += 3;
        if (lower.contains("value #(")) score += 3;
        if (lower.contains("conv #(")) score += 3;
        if (lower.contains("cond #(")) score += 3;
        if (lower.contains("switch #(")) score += 3;
        if (lower.contains("corresponding #(")) score += 3;
        
        // ABAP predicates
        if (lower.contains("is initial")) score += 2;
        if (lower.contains("is not initial")) score += 3;
        if (lower.contains("is bound")) score += 3;
        if (lower.contains("is assigned")) score += 3;
        if (lower.contains("line_exists(")) score += 3;
        
        // ABAP transaction/database
        if (lower.contains("commit work")) score += 3;
        if (lower.contains("rollback work")) score += 3;
        if (lower.contains("authority-check")) score += 3;
        
        // ABAP message statement
        if (lower.contains("message ") && lower.contains(" type ")) score += 2;
        
        // ABAP-specific functions
        if (lower.contains("lines(")) score += 2;
        if (lower.contains("strlen(") && !content.contains("strlen(const")) score += 1; // C has strlen too
        if (lower.contains("xstrlen(")) score += 3;
        if (lower.contains("boolc(")) score += 3;
        if (lower.contains("xsdbool(")) score += 3;
        
        return score;
    }
    
    // ===== HTML Keywords =====
    private static int calculateHtmlScore(String content, String lower) {
        int score = 0;
        if (lower.contains("<!doctype html")) score += 5;
        if (lower.contains("<html") && lower.contains("</html>")) score += 5;
        if (lower.contains("<head>") || lower.contains("<head ")) score += 3;
        if (lower.contains("<body>") || lower.contains("<body ")) score += 3;
        if (lower.contains("<div") && lower.contains("</div>")) score += 3;
        if (lower.contains("<span") && lower.contains("</span>")) score += 3;
        if (lower.contains("<p>") || lower.contains("<p ")) score += 2;
        if (lower.contains("<h1") || lower.contains("<h2") || lower.contains("<h3")) score += 2;
        if (lower.contains("<ul") || lower.contains("<ol") || lower.contains("<li")) score += 2;
        if (lower.contains("<table") || lower.contains("<tr") || lower.contains("<td")) score += 3;
        if (lower.contains("<form") && lower.contains("</form>")) score += 3;
        if (lower.contains("<input") || lower.contains("<button") || lower.contains("<select")) score += 2;
        if (lower.contains("<a ") && lower.contains("href=")) score += 3;
        if (lower.contains("<img") && lower.contains("src=")) score += 3;
        if (lower.contains("<link") && lower.contains("rel=")) score += 3;
        if (lower.contains("<meta") && (lower.contains("charset") || lower.contains("content="))) score += 3;
        if (lower.contains("<script") && lower.contains("</script>")) score += 3;
        if (lower.contains("<style") && lower.contains("</style>")) score += 3;
        if (lower.contains("<nav") || lower.contains("<header") || lower.contains("<footer")) score += 2;
        if (lower.contains("<section") || lower.contains("<article") || lower.contains("<aside")) score += 2;
        if (lower.contains("<main") || lower.contains("<figure") || lower.contains("<figcaption")) score += 2;
        if (lower.contains("<br") || lower.contains("<hr")) score += 1;
        if (lower.contains("<strong") || lower.contains("<em") || lower.contains("<b>")) score += 1;
        if (lower.contains("<iframe") || lower.contains("<video") || lower.contains("<audio")) score += 2;
        if (lower.contains("<canvas") || lower.contains("<svg")) score += 2;
        if (lower.contains("class=\"") || lower.contains("id=\"") || lower.contains("style=\"")) score += 2;
        
        return score;
    }
    
    // ===== Go Keywords =====
    private static int calculateGoScore(String content) {
        int score = 0;
        if (content.contains("package main")) score += 5;
        if (content.contains("package ") && content.contains("import")) score += 3;
        if (content.contains("func ") && content.contains(") {")) score += 3;
        if (content.contains("func main()")) score += 5;
        if (content.contains(":= ")) score += 3;
        if (content.contains("go func")) score += 4;
        if (content.contains("interface{}")) score += 3;
        if (content.contains("struct {")) score += 3;
        if (content.contains("*sql.DB") || content.contains("*http.")) score += 3;
        if (content.contains("http.Handle") || content.contains("http.ListenAndServe")) score += 3;
        if (content.contains("fmt.Print") || content.contains("fmt.Sprintf")) score += 3;
        if (content.contains("log.Print") || content.contains("log.Fatal")) score += 3;
        if (content.contains("err != nil") || content.contains("err == nil")) score += 3;
        if (content.contains("if err != nil")) score += 4;
        if (content.contains("defer ")) score += 3;
        if (content.contains("go ") && content.contains("chan ")) score += 3;
        if (content.contains("make(") && (content.contains("[]") || content.contains("map[") || content.contains("chan "))) score += 3;
        if (content.contains("append(")) score += 2;
        if (content.contains("range ")) score += 2;
        if (content.contains("select {")) score += 2;
        if (content.contains("case <-")) score += 3;
        if (content.contains("fallthrough")) score += 3;
        if (content.contains("type ") && content.contains(" struct")) score += 3;
        if (content.contains("type ") && content.contains(" interface")) score += 3;
        if (content.contains("json.Marshal") || content.contains("json.Unmarshal")) score += 3;
        if (content.contains("ioutil.") || content.contains("os.")) score += 2;
        if (content.contains("filepath.") || content.contains("strings.")) score += 2;
        if (content.contains("strconv.") || content.contains("time.")) score += 2;
        if (content.contains("context.") || content.contains("sync.")) score += 2;
        if (content.contains("var ") && content.contains(" = ") && !content.contains(";")) score += 2;
        if (content.contains("const (")) score += 3;
        if (content.contains("nil") && content.contains("func ")) score += 2;
        
        return score;
    }
    
    // ===== Java Keywords =====
    private static int calculateJavaScore(String content) {
        int score = 0;
        if (content.contains("public class ")) score += 3;
        if (content.contains("private class ")) score += 3;
        if (content.contains("protected class ")) score += 3;
        if (content.contains("public interface ")) score += 3;
        if (content.contains("public enum ")) score += 3;
        if (content.contains("public abstract ")) score += 3;
        if (content.contains("private void ")) score += 3;
        if (content.contains("public void ")) score += 3;
        if (content.contains("protected void ")) score += 3;
        if (content.contains("private static ")) score += 3;
        if (content.contains("public static ")) score += 3;
        if (content.contains("public static void main")) score += 5;
        if (content.contains("import java.")) score += 4;
        if (content.contains("import javax.")) score += 4;
        if (content.contains("import org.") && content.contains(";")) score += 3;
        if (content.contains("@Override")) score += 3;
        if (content.contains("@Autowired")) score += 3;
        if (content.contains("@Component")) score += 3;
        if (content.contains("@Service")) score += 3;
        if (content.contains("@Repository")) score += 3;
        if (content.contains("@Controller")) score += 3;
        if (content.contains("@RestController")) score += 3;
        if (content.contains("@RequestMapping")) score += 3;
        if (content.contains("@GetMapping") || content.contains("@PostMapping")) score += 3;
        if (content.contains("@Entity") || content.contains("@Table")) score += 3;
        if (content.contains("@Id") || content.contains("@Column")) score += 3;
        if (content.contains("@Test") || content.contains("@Before") || content.contains("@After")) score += 2;
        if (content.contains("System.out.print")) score += 4;
        if (content.contains("System.err.")) score += 4;
        if (content.contains("new ArrayList") || content.contains("new LinkedList")) score += 3;
        if (content.contains("new HashMap") || content.contains("new HashSet")) score += 3;
        if (content.contains("new StringBuilder") || content.contains("new StringBuffer")) score += 3;
        if (content.contains("extends ") && content.contains("{")) score += 2;
        if (content.contains("implements ") && content.contains("{")) score += 2;
        if (content.contains("throws ")) score += 2;
        if (content.contains("try {") || content.contains("catch (") || content.contains("finally {")) score += 2;
        if (content.contains("synchronized ")) score += 3;
        if (content.contains("volatile ")) score += 3;
        if (content.contains("transient ")) score += 3;
        if (content.contains("instanceof ")) score += 2;
        if (content.contains(".stream()") || content.contains(".collect(")) score += 3;
        if (content.contains(".forEach(") || content.contains(".map(")) score += 2;
        if (content.contains("Optional.")) score += 3;
        if (content.contains("CompletableFuture")) score += 3;
        if (content.contains("Logger.") || content.contains("LoggerFactory.")) score += 2;
        
        return score;
    }
    
    // ===== TypeScript Keywords =====
    private static int calculateTypeScriptScore(String content) {
        int score = 0;
        if (content.contains(": string")) score += 3;
        if (content.contains(": number")) score += 3;
        if (content.contains(": boolean")) score += 3;
        if (content.contains(": any")) score += 3;
        if (content.contains(": void")) score += 3;
        if (content.contains(": null")) score += 3;
        if (content.contains(": undefined")) score += 3;
        if (content.contains(": never")) score += 3;
        if (content.contains(": unknown")) score += 3;
        if (content.contains(": object")) score += 3;
        if (content.contains("string[]") || content.contains("number[]") || content.contains("boolean[]")) score += 3;
        if (content.contains("Array<")) score += 3;
        if (content.contains("interface ") && content.contains("{")) score += 3;
        if (content.contains("type ") && content.contains(" = ") && content.contains(";")) score += 3;
        if (content.contains("<T>") || content.contains("<T,") || content.contains("<T extends")) score += 3;
        if (content.contains("as string") || content.contains("as number") || content.contains("as any")) score += 3;
        if (content.contains(": Promise<")) score += 3;
        if (content.contains(": Observable<")) score += 3;
        if (content.contains("readonly ")) score += 3;
        if (content.contains("private ") && content.contains(": ")) score += 3;
        if (content.contains("public ") && content.contains(": ")) score += 3;
        if (content.contains("protected ") && content.contains(": ")) score += 3;
        if (content.contains("constructor(") && content.contains(": ")) score += 3;
        if (content.contains("implements ")) score += 2;
        if (content.contains("extends ") && content.contains("<")) score += 3;
        if (content.contains("keyof ")) score += 3;
        if (content.contains("typeof ") && content.contains(": ")) score += 3;
        if (content.contains("Partial<") || content.contains("Required<") || content.contains("Pick<")) score += 3;
        if (content.contains("Omit<") || content.contains("Record<") || content.contains("Exclude<")) score += 3;
        if (content.contains("Extract<") || content.contains("NonNullable<")) score += 3;
        if (content.contains("ReturnType<") || content.contains("Parameters<")) score += 3;
        if (content.contains("enum ") && content.contains("{")) score += 3;
        if (content.contains("namespace ") && content.contains("{")) score += 3;
        if (content.contains("declare ")) score += 3;
        if (content.contains("abstract class ")) score += 3;
        if (content.contains("@Injectable") || content.contains("@Component")) score += 3;
        if (content.contains("@Input") || content.contains("@Output")) score += 3;
        
        return score;
    }
    
    // ===== JavaScript Keywords =====
    private static int calculateJavaScriptScore(String content) {
        int score = 0;
        if (content.contains("function ") && content.contains("(") && content.contains(") {")) score += 3;
        if (content.contains("function(")) score += 3;
        if (content.contains("const ") && content.contains(" = ")) score += 2;
        if (content.contains("let ") && content.contains(" = ")) score += 2;
        if (content.contains("var ") && content.contains(" = ")) score += 2;
        if (content.contains("=> {") || content.contains("=> ")) score += 3;
        if (content.contains("async ") || content.contains("await ")) score += 3;
        if (content.contains("console.log") || content.contains("console.error") || content.contains("console.warn")) score += 3;
        if (content.contains("document.")) score += 3;
        if (content.contains("window.")) score += 3;
        if (content.contains("addEventListener") || content.contains("removeEventListener")) score += 3;
        if (content.contains("getElementById") || content.contains("querySelector")) score += 3;
        if (content.contains("createElement") || content.contains("appendChild")) score += 3;
        if (content.contains("innerHTML") || content.contains("textContent")) score += 3;
        if (content.contains("className") || content.contains("classList")) score += 3;
        if (content.contains("setAttribute") || content.contains("getAttribute")) score += 3;
        if (content.contains("require(")) score += 3;
        if (content.contains("module.exports")) score += 3;
        if (content.contains("export default")) score += 3;
        if (content.contains("export const") || content.contains("export function")) score += 3;
        if (content.contains("export class")) score += 3;
        if (content.contains("import ") && content.contains(" from ")) score += 3;
        if (content.contains("import {")) score += 2;
        if (content.contains("new Promise")) score += 3;
        if (content.contains(".then(") || content.contains(".catch(") || content.contains(".finally(")) score += 3;
        if (content.contains("fetch(")) score += 3;
        if (content.contains("JSON.parse") || content.contains("JSON.stringify")) score += 3;
        if (content.contains("Array.") || content.contains("Object.") || content.contains("String.")) score += 2;
        if (content.contains(".map(") || content.contains(".filter(") || content.contains(".reduce(")) score += 2;
        if (content.contains(".forEach(") || content.contains(".find(") || content.contains(".some(")) score += 2;
        if (content.contains(".every(") || content.contains(".includes(")) score += 2;
        if (content.contains("setTimeout") || content.contains("setInterval")) score += 3;
        if (content.contains("localStorage") || content.contains("sessionStorage")) score += 3;
        if (content.contains("try {") || content.contains("catch (")) score += 2;
        if (content.contains("throw new Error")) score += 3;
        if (content.contains("typeof ") || content.contains("instanceof ")) score += 2;
        if (content.contains("null") || content.contains("undefined")) score += 2;
        if (content.contains("true") || content.contains("false")) score += 1;
        if (content.contains("this.") && !content.contains("$this->")) score += 2;
        if (content.contains("class ") && content.contains("constructor(")) score += 3;
        if (content.contains("extends ") && content.contains("super(")) score += 3;
        if (content.contains("get ") && content.contains("() {")) score += 2;
        if (content.contains("set ") && content.contains("(") && content.contains(") {")) score += 2;
        if (content.contains("static ") && content.contains("() {")) score += 2;
        
        return score;
    }
    
    // ===== Python Keywords =====
    private static int calculatePythonScore(String content) {
        int score = 0;
        if (content.contains("def ") && content.contains("):")) score += 5;
        if (content.contains("async def ")) score += 5;
        if (content.contains("class ") && content.contains("):")) score += 5;
        if (content.contains("import ") && !content.contains("import {") && !content.contains("import java")) score += 2;
        if (content.contains("from ") && content.contains(" import ")) score += 4;
        if (content.contains("if __name__")) score += 5;
        if (content.contains("self.")) score += 3;
        if (content.contains("self,")) score += 3;
        if (content.contains("cls.") || content.contains("cls,")) score += 3;
        if (content.contains("print(")) score += 2;
        if (content.contains("elif ")) score += 4;
        if (content.contains("except ") || content.contains("except:")) score += 4;
        if (content.contains("raise ")) score += 2;
        if (content.contains("finally:")) score += 3;
        if (content.contains("with ") && content.contains(" as ")) score += 3;
        if (content.contains("lambda ")) score += 3;
        if (content.contains("yield ")) score += 3;
        if (content.contains("await ")) score += 2;
        if (content.contains("pass")) score += 2;
        if (content.contains("continue")) score += 1;
        if (content.contains("break")) score += 1;
        if (content.contains("return ")) score += 1;
        if (content.contains("global ")) score += 3;
        if (content.contains("nonlocal ")) score += 3;
        if (content.contains("assert ")) score += 2;
        if (content.contains("del ")) score += 2;
        if (content.contains("True") || content.contains("False") || content.contains("None")) score += 2;
        if (content.contains(" in ") && content.contains(":")) score += 2;
        if (content.contains("for ") && content.contains(" in ") && content.contains(":")) score += 4;
        if (content.contains("while ") && content.contains(":")) score += 3;
        if (content.contains("if ") && content.contains(":") && !content.contains("{")) score += 2;
        if (content.contains("else:")) score += 3;
        if (content.contains("@property") || content.contains("@staticmethod") || content.contains("@classmethod")) score += 4;
        if (content.contains("@abstractmethod")) score += 4;
        if (content.contains("__init__") || content.contains("__str__") || content.contains("__repr__")) score += 5;
        if (content.contains("__name__") || content.contains("__main__")) score += 4;
        if (content.contains("__class__") || content.contains("__dict__")) score += 4;
        if (content.contains("len(") || content.contains("range(") || content.contains("enumerate(")) score += 2;
        if (content.contains("list(") || content.contains("dict(") || content.contains("set(") || content.contains("tuple(")) score += 2;
        if (content.contains("str(") || content.contains("int(") || content.contains("float(")) score += 1;
        if (content.contains("open(") || content.contains("read(") || content.contains("write(")) score += 1;
        if (content.contains(".append(") || content.contains(".extend(") || content.contains(".pop(")) score += 2;
        if (content.contains(".keys()") || content.contains(".values()") || content.contains(".items()")) score += 3;
        if (content.contains(".join(") || content.contains(".split(") || content.contains(".strip()")) score += 2;
        if (content.contains(".format(") || content.contains("f\"") || content.contains("f'")) score += 3;
        if (content.contains("try:") || content.contains("except ")) score += 3;
        if (content.contains("# ") && content.contains("\n")) score += 1;
        
        return score;
    }
    
    // ===== C Keywords =====
    private static int calculateCScore(String content) {
        int score = 0;
        if (content.contains("#include <stdio.h>")) score += 10;
        if (content.contains("#include <stdlib.h>")) score += 10;
        if (content.contains("#include \"")) score += 5;
        if (content.contains("int main(")) score += 5;
        if (content.contains("void main(")) score += 5;
        if (content.contains("printf(")) score += 5;
        if (content.contains("scanf(")) score += 5;
        if (content.contains("malloc(")) score += 5;
        if (content.contains("free(")) score += 5;
        if (content.contains("struct ")) score += 3;
        if (content.contains("typedef struct")) score += 5;
        if (content.contains("enum ")) score += 3;
        if (content.contains("union ")) score += 3;
        if (content.contains("const ")) score += 2;
        if (content.contains("static ")) score += 2;
        if (content.contains("volatile ")) score += 2;
        if (content.contains("extern ")) score += 2;
        if (content.contains("register ")) score += 2;
        if (content.contains("auto ")) score += 2;
        if (content.contains("unsigned ")) score += 2;
        if (content.contains("signed ")) score += 2;
        if (content.contains("short ")) score += 2;
        if (content.contains("long ")) score += 2;
        if (content.contains("char ")) score += 2;
        if (content.contains("float ")) score += 2;
        if (content.contains("double ")) score += 2;
        if (content.contains("void ")) score += 2;
        if (content.contains("sizeof(")) score += 3;
        if (content.contains("NULL")) score += 2;
        if (content.contains("->")) score += 3;
        if (content.contains("FILE *")) score += 5;
        if (content.contains("fopen(")) score += 4;
        if (content.contains("fclose(")) score += 4;
        if (content.contains("fprintf(")) score += 4;
        if (content.contains("fscanf(")) score += 4;
        if (content.contains("return 0;")) score += 3;
        if (content.contains("/*") && content.contains("*/")) score += 2;
        if (content.contains("//")) score += 1;
        if (content.contains("#define ")) score += 3;
        if (content.contains("#ifdef ")) score += 3;
        if (content.contains("#ifndef ")) score += 3;
        if (content.contains("#endif")) score += 3;
        
        return score;
    }
    
    // ===== C++ Keywords (additional to C) =====
    private static int calculateCppScore(String content) {
        int score = 0;
        if (content.contains("cout <<") || content.contains("cout<<")) score += 5;
        if (content.contains("cin >>") || content.contains("cin>>")) score += 5;
        if (content.contains("cerr <<")) score += 5;
        if (content.contains("endl")) score += 3;
        if (content.contains("std::")) score += 5;
        if (content.contains("using namespace")) score += 4;
        if (content.contains("class ") && content.contains("public:")) score += 4;
        if (content.contains("class ") && content.contains("private:")) score += 4;
        if (content.contains("class ") && content.contains("protected:")) score += 4;
        if (content.contains("template<") || content.contains("template <")) score += 5;
        if (content.contains("typename ")) score += 3;
        if (content.contains("nullptr")) score += 3;
        if (content.contains("new ") && content.contains(";")) score += 2;
        if (content.contains("delete ") || content.contains("delete[] ")) score += 3;
        if (content.contains("virtual ")) score += 3;
        if (content.contains("override")) score += 3;
        if (content.contains("final") && content.contains("class ")) score += 3;
        if (content.contains("constexpr ")) score += 3;
        if (content.contains("auto ") && content.contains(" = ")) score += 2;
        if (content.contains("decltype(")) score += 3;
        if (content.contains("noexcept")) score += 3;
        if (content.contains("explicit ")) score += 3;
        if (content.contains("friend ")) score += 3;
        if (content.contains("mutable ")) score += 3;
        if (content.contains("operator") && content.contains("(")) score += 3;
        if (content.contains("dynamic_cast<") || content.contains("static_cast<")) score += 4;
        if (content.contains("reinterpret_cast<") || content.contains("const_cast<")) score += 4;
        if (content.contains("try {") && content.contains("catch (")) score += 2;
        if (content.contains("throw ")) score += 2;
        if (content.contains("namespace ") && content.contains("{")) score += 3;
        if (content.contains("::") && content.contains("(")) score += 3;
        if (content.contains("vector<") || content.contains("string ")) score += 3;
        if (content.contains("map<") || content.contains("set<")) score += 3;
        if (content.contains("list<") || content.contains("deque<")) score += 3;
        if (content.contains("unique_ptr<") || content.contains("shared_ptr<") || content.contains("weak_ptr<")) score += 4;
        if (content.contains("make_unique<") || content.contains("make_shared<")) score += 4;
        if (content.contains("std::string") || content.contains("std::vector")) score += 4;
        if (content.contains("std::cout") || content.contains("std::cin")) score += 4;
        if (content.contains("std::endl") || content.contains("std::move")) score += 4;
        if (content.contains("std::function") || content.contains("std::thread")) score += 4;
        if (content.contains("std::mutex") || content.contains("std::lock_guard")) score += 4;
        if (content.contains("std::async") || content.contains("std::future")) score += 4;
        if (content.contains("std::optional") || content.contains("std::variant")) score += 4;
        if (content.contains("std::any") || content.contains("std::tuple")) score += 4;
        if (content.contains("std::pair") || content.contains("std::array")) score += 4;
        if (content.contains("std::begin(") || content.contains("std::end(")) score += 4;
        if (content.contains("std::sort(") || content.contains("std::find(")) score += 4;
        if (content.contains("std::transform(") || content.contains("std::for_each(")) score += 4;
        if (content.contains("std::accumulate(")) score += 4;
        if (content.contains("std::chrono::") || content.contains("std::filesystem::")) score += 4;
        
        return score;
    }
    
    // ===== C# Keywords =====
    private static int calculateCSharpScore(String content) {
        int score = 0;
        if (content.contains("using System")) score += 5;
        if (content.contains("using Microsoft.")) score += 5;
        if (content.contains("using UnityEngine")) score += 5;
        if (content.contains("namespace ") && content.contains("{") && !content.contains("<?php")) score += 3;
        if (content.contains("public class ") && content.contains(";")) score += 3;
        if (content.contains("private class ")) score += 3;
        if (content.contains("protected class ")) score += 3;
        if (content.contains("internal class ")) score += 3;
        if (content.contains("public interface ")) score += 3;
        if (content.contains("public enum ")) score += 3;
        if (content.contains("public struct ")) score += 3;
        if (content.contains("public void ")) score += 3;
        if (content.contains("private void ")) score += 3;
        if (content.contains("public static void ")) score += 3;
        if (content.contains("public async ")) score += 4;
        if (content.contains("private async ")) score += 4;
        if (content.contains("async Task")) score += 5;
        if (content.contains("async void")) score += 5;
        if (content.contains("await ")) score += 3;
        if (content.contains("Console.Write") || content.contains("Console.Read")) score += 4;
        if (content.contains("Debug.Log")) score += 4;
        if (content.contains("[Attribute]")) score += 4;
        if (content.contains("[Serializable]")) score += 4;
        if (content.contains("[SerializeField]")) score += 4;
        if (content.contains("[HttpGet]") || content.contains("[HttpPost]")) score += 4;
        if (content.contains("[Route(") || content.contains("[ApiController]")) score += 4;
        if (content.contains("[TestMethod]") || content.contains("[TestClass]")) score += 4;
        if (content.contains("[Fact]") || content.contains("[Theory]")) score += 4;
        if (content.contains("var ") && content.contains(" = new ") && content.contains(";")) score += 3;
        if (content.contains("string ") && content.contains(" = ") && content.contains(";")) score += 2;
        if (content.contains("int ") && content.contains(" = ") && content.contains(";")) score += 2;
        if (content.contains("bool ") && content.contains(" = ") && content.contains(";")) score += 2;
        if (content.contains("List<") || content.contains("Dictionary<")) score += 3;
        if (content.contains("IEnumerable<") || content.contains("IList<")) score += 3;
        if (content.contains("Action<") || content.contains("Func<")) score += 3;
        if (content.contains("Task<") || content.contains("ValueTask<")) score += 3;
        if (content.contains("get;") || content.contains("set;")) score += 4;
        if (content.contains("{ get; set; }") || content.contains("{ get; }")) score += 5;
        if (content.contains("=> ") && content.contains(";")) score += 3;
        if (content.contains("base.") || content.contains("this.")) score += 2;
        if (content.contains("virtual ") || content.contains("override ")) score += 3;
        if (content.contains("abstract ") || content.contains("sealed ")) score += 3;
        if (content.contains("readonly ")) score += 3;
        if (content.contains("partial class ")) score += 4;
        if (content.contains("static class ")) score += 3;
        if (content.contains("new()") || content.contains("where T :")) score += 4;
        if (content.contains("is ") && content.contains(" pattern")) score += 3;
        if (content.contains("switch ") && content.contains(" => ")) score += 3;
        if (content.contains("nameof(")) score += 3;
        if (content.contains("typeof(")) score += 2;
        if (content.contains("?.") || content.contains("??") || content.contains("?[")) score += 3;
        if (content.contains("throw new ")) score += 2;
        if (content.contains("try {") || content.contains("catch (") || content.contains("finally {")) score += 2;
        if (content.contains("lock (")) score += 3;
        if (content.contains("LINQ") || content.contains(".Where(") || content.contains(".Select(")) score += 3;
        if (content.contains(".OrderBy(") || content.contains(".GroupBy(")) score += 3;
        if (content.contains(".FirstOrDefault(") || content.contains(".ToList()")) score += 3;
        if (content.contains("StringBuilder") || content.contains("StringComparison")) score += 3;
        if (content.contains("DateTime") || content.contains("TimeSpan")) score += 3;
        if (content.contains("Guid.") || content.contains("Path.")) score += 3;
        if (content.contains("File.") || content.contains("Directory.")) score += 3;
        if (content.contains("HttpClient") || content.contains("WebRequest")) score += 3;
        if (content.contains("JsonConvert.") || content.contains("JsonSerializer.")) score += 3;
        
        return score;
    }
    
    // ===== CSS Keywords =====
    private static int calculateCssScore(String content) {
        int score = 0;
        if (content.contains("{") && content.contains("}") && content.contains(":")) score += 2;
        if (content.contains("body {") || content.contains("html {")) score += 5;
        if (content.contains("div {") || content.contains("span {")) score += 3;
        if (content.contains(".class") || content.contains("#id")) score += 2;
        if (content.contains("@media")) score += 5;
        if (content.contains("@import")) score += 3;
        if (content.contains("@keyframes")) score += 5;
        if (content.contains("@font-face")) score += 5;
        if (content.contains("!important")) score += 4;
        if (content.contains("px;") || content.contains("em;") || content.contains("rem;") || content.contains("%;")) score += 3;
        if (content.contains("color:") || content.contains("background-color:")) score += 3;
        if (content.contains("font-size:") || content.contains("font-family:")) score += 3;
        if (content.contains("margin:") || content.contains("padding:")) score += 3;
        if (content.contains("border:") || content.contains("border-radius:")) score += 3;
        if (content.contains("display:") && (content.contains("block") || content.contains("flex") || content.contains("grid"))) score += 3;
        if (content.contains("position:") && (content.contains("absolute") || content.contains("relative") || content.contains("fixed"))) score += 3;
        if (content.contains("width:") || content.contains("height:")) score += 2;
        if (content.contains("text-align:") || content.contains("text-decoration:")) score += 3;
        if (content.contains("cursor:") && content.contains("pointer")) score += 3;
        if (content.contains("z-index:")) score += 3;
        if (content.contains("opacity:")) score += 3;
        if (content.contains("transition:") || content.contains("transform:")) score += 3;
        if (content.contains("box-shadow:")) score += 3;
        if (content.contains("flex-direction:") || content.contains("justify-content:") || content.contains("align-items:")) score += 4;
        if (content.contains("grid-template-columns:")) score += 4;
        if (content.contains("/*") && content.contains("*/")) score += 2;
        
        // Negative checks - CSS shouldn't have these
        if (content.contains("<html") || content.contains("<!DOCTYPE")) score -= 5;
        if (content.contains("public class ") || content.contains("private void ")) score -= 10;
        if (content.contains("function(") || content.contains("=>")) score -= 5;
        
        return score;
    }

    // ===== SQL Keywords =====
    private static int calculateSqlScore(String lower) {
        int score = 0;
        if (lower.contains("select ") && lower.contains(" from ")) score += 5;
        if (lower.contains("insert into ") && lower.contains(" values")) score += 5;
        if (lower.contains("update ") && lower.contains(" set ")) score += 5;
        if (lower.contains("delete from ")) score += 5;
        if (lower.contains("create table ")) score += 5;
        if (lower.contains("drop table ")) score += 5;
        if (lower.contains("alter table ")) score += 5;
        if (lower.contains(" where ")) score += 3;
        if (lower.contains(" group by ")) score += 4;
        if (lower.contains(" order by ")) score += 4;
        if (lower.contains(" having ")) score += 4;
        if (lower.contains(" join ") && lower.contains(" on ")) score += 4;
        if (lower.contains("inner join") || lower.contains("left join") || lower.contains("right join")) score += 5;
        if (lower.contains("union") || lower.contains("union all")) score += 4;
        if (lower.contains("distinct ")) score += 3;
        if (lower.contains("limit ")) score += 3;
        if (lower.contains("offset ")) score += 3;
        if (lower.contains("primary key")) score += 4;
        if (lower.contains("foreign key")) score += 4;
        if (lower.contains("references ")) score += 3;
        if (lower.contains("constraint ")) score += 3;
        if (lower.contains("index ")) score += 3;
        if (lower.contains("view ")) score += 3;
        if (lower.contains("trigger ")) score += 3;
        if (lower.contains("procedure ")) score += 3;
        if (lower.contains("declare ")) score += 3;
        if (lower.contains("begin") && lower.contains("end")) score += 2;
        if (lower.contains("varchar") || lower.contains("integer") || lower.contains("boolean")) score += 3;
        if (lower.contains("timestamp") || lower.contains("datetime")) score += 3;
        if (lower.contains("null") && !lower.contains("public")) score += 2;
        if (lower.contains("count(") || lower.contains("sum(") || lower.contains("avg(")) score += 3;
        if (lower.contains("max(") || lower.contains("min(")) score += 3;
        if (lower.contains("-- ")) score += 2;
        
        return score;
    }

    // ===== XML Keywords =====
    private static int calculateXmlScore(String lower) {
        int score = 0;
        if (lower.contains("<?xml version=")) score += 10;
        if (lower.contains("xmlns:") || lower.contains("xmlns=")) score += 5;
        if (lower.contains("xsi:schemalocation")) score += 5;
        if (lower.contains("<!cdata[")) score += 5;
        if (lower.contains("<!--") && lower.contains("-->")) score += 2;
        if (lower.contains("</") && lower.contains(">")) score += 3;
        if (lower.contains("/>")) score += 2;
        if (lower.contains("=\"") && lower.contains("\"")) score += 1;
        
        // Common XML-based formats
        if (lower.contains("<project") && lower.contains("</project>")) score += 3; // Maven
        if (lower.contains("<dependency>") && lower.contains("</dependency>")) score += 3; // Maven
        if (lower.contains("<beans") && lower.contains("</beans>")) score += 3; // Spring
        if (lower.contains("<web-app") && lower.contains("</web-app>")) score += 3; // web.xml
        if (lower.contains("<soap:envelope")) score += 5; // SOAP
        if (lower.contains("<xsl:stylesheet")) score += 5; // XSLT
        if (lower.contains("<svg") && lower.contains("</svg>")) score += 3; // SVG
        
        return score;
    }

    // ===== PHP Keywords =====
    private static int calculatePhpScore(String content) {
        int score = 0;
        if (content.contains("<?php")) score += 10;
        if (content.contains("<?= ")) score += 5;
        if (content.contains("namespace ") && content.contains(";")) score += 3;
        if (content.contains("use ") && content.contains("\\") && content.contains(";")) score += 3;
        if (content.contains("$this->")) score += 5;
        if (content.contains("public function ")) score += 3;
        if (content.contains("private function ")) score += 3;
        if (content.contains("protected function ")) score += 3;
        if (content.contains("function ") && content.contains("(") && content.contains(")")) score += 2;
        if (content.contains("echo ")) score += 2;
        if (content.contains("print_r(")) score += 3;
        if (content.contains("var_dump(")) score += 3;
        if (content.contains("require_once")) score += 4;
        if (content.contains("include_once")) score += 4;
        if (content.contains("require ")) score += 3;
        if (content.contains("include ")) score += 3;
        if (content.contains("array(")) score += 3;
        if (content.contains("foreach (") && content.contains(" as ")) score += 3;
        if (content.contains("implode(")) score += 3;
        if (content.contains("explode(")) score += 3;
        if (content.contains("strpos(")) score += 3;
        if (content.contains("strlen(")) score += 2;
        if (content.contains("substr(")) score += 2;
        if (content.contains("$_GET")) score += 4;
        if (content.contains("$_POST")) score += 4;
        if (content.contains("$_SESSION")) score += 4;
        if (content.contains("$_SERVER")) score += 4;
        if (content.contains("try {") && content.contains("catch (")) score += 2;
        if (content.contains("throw new Exception")) score += 3;
        if (content.contains("class ") && content.contains(" extends ")) score += 3;
        if (content.contains("interface ")) score += 2;
        if (content.contains("trait ")) score += 3;
        if (content.contains("const ")) score += 2;
        if (content.contains("static ")) score += 2;
        if (content.contains("self::")) score += 4;
        if (content.contains("parent::")) score += 4;
        if (content.contains("->")) score += 2;
        if (content.contains("::")) score += 2;
        if (content.contains("=>")) score += 2;
        
        return score;
    }

    // ===== Ruby Keywords =====
    private static int calculateRubyScore(String content) {
        int score = 0;
        if (content.contains("def ") && !content.contains("{") && !content.contains("):")) score += 3;
        if (content.contains("end") && (content.contains("def ") || content.contains("if ") || content.contains("do"))) score += 3;
        if (content.contains("class ") && content.contains("<")) score += 3;
        if (content.contains("module ")) score += 3;
        if (content.contains("require ")) score += 2;
        if (content.contains("require_relative ")) score += 4;
        if (content.contains("include ")) score += 2;
        if (content.contains("attr_accessor")) score += 5;
        if (content.contains("attr_reader")) score += 5;
        if (content.contains("attr_writer")) score += 5;
        if (content.contains("initialize")) score += 4;
        if (content.contains("puts ")) score += 3;
        if (content.contains("p ")) score += 2;
        if (content.contains("raise ")) score += 2;
        if (content.contains("yield")) score += 3;
        if (content.contains("unless ")) score += 4;
        if (content.contains("elsif ")) score += 4;
        if (content.contains("when ")) score += 2;
        if (content.contains("case ")) score += 2;
        if (content.contains("begin") && content.contains("rescue")) score += 4;
        if (content.contains("ensure")) score += 3;
        if (content.contains("nil")) score += 2;
        if (content.contains("true") || content.contains("false")) score += 1;
        if (content.contains("self.")) score += 2;
        if (content.contains("@")) score += 2;
        if (content.contains("@@")) score += 3;
        if (content.contains("=>")) score += 2;
        if (content.contains("do |")) score += 4;
        if (content.contains(".each do")) score += 4;
        if (content.contains(".map do")) score += 4;
        if (content.contains("lambda")) score += 2;
        if (content.contains("Proc.new")) score += 4;
        if (content.contains("gem ")) score += 3;
        
        return score;
    }

    // ===== Rust Keywords =====
    private static int calculateRustScore(String content) {
        int score = 0;
        if (content.contains("fn ") && content.contains("{")) score += 4;
        if (content.contains("fn main()")) score += 5;
        if (content.contains("let mut ")) score += 5;
        if (content.contains("let ")) score += 2;
        if (content.contains("pub fn ")) score += 4;
        if (content.contains("pub struct ")) score += 4;
        if (content.contains("pub enum ")) score += 4;
        if (content.contains("pub mod ")) score += 4;
        if (content.contains("pub use ")) score += 4;
        if (content.contains("pub trait ")) score += 4;
        if (content.contains("impl ")) score += 4;
        if (content.contains("struct ") && content.contains("{")) score += 3;
        if (content.contains("enum ") && content.contains("{")) score += 3;
        if (content.contains("mod ")) score += 3;
        if (content.contains("use std::")) score += 5;
        if (content.contains("crate::")) score += 4;
        if (content.contains("super::")) score += 4;
        if (content.contains("self::")) score += 4;
        if (content.contains("match ")) score += 3;
        if (content.contains("=>")) score += 2;
        if (content.contains("println!")) score += 5;
        if (content.contains("format!")) score += 5;
        if (content.contains("vec!")) score += 5;
        if (content.contains("panic!")) score += 5;
        if (content.contains("Ok(") || content.contains("Err(")) score += 3;
        if (content.contains("Option<") || content.contains("Result<")) score += 4;
        if (content.contains("Some(") || content.contains("None")) score += 3;
        if (content.contains("String::from")) score += 4;
        if (content.contains("&str")) score += 4;
        if (content.contains("&mut ")) score += 4;
        if (content.contains("unsafe {")) score += 4;
        if (content.contains("loop {")) score += 3;
        if (content.contains("while let ")) score += 4;
        if (content.contains("if let ")) score += 4;
        if (content.contains("for ") && content.contains(" in ")) score += 3;
        if (content.contains("as ")) score += 2;
        if (content.contains("where ")) score += 3;
        if (content.contains("#[derive(")) score += 5;
        if (content.contains("#[cfg(")) score += 5;
        if (content.contains("#[test]")) score += 5;
        if (content.contains("->")) score += 2;
        if (content.contains("::")) score += 2;
        
        return score;
    }
    
    // ===== Bash/Shell Keywords =====
    private static int calculateBashScore(String content) {
        int score = 0;
        if (content.contains("#!/bin/bash") || content.contains("#!/bin/sh")) score += 10;
        if (content.contains("echo ")) score += 2;
        if (content.contains("if [") || content.contains("if [[")) score += 4;
        if (content.contains("fi") && content.contains("if")) score += 4;
        if (content.contains("then")) score += 2;
        if (content.contains("else")) score += 2;
        if (content.contains("elif")) score += 3;
        if (content.contains("case ") && content.contains(" in")) score += 3;
        if (content.contains("esac")) score += 4;
        if (content.contains("for ") && content.contains(" in ")) score += 3;
        if (content.contains("done") && (content.contains("for") || content.contains("while"))) score += 3;
        if (content.contains("while [") || content.contains("while [[")) score += 4;
        if (content.contains("function ")) score += 3;
        if (content.contains("local ")) score += 3;
        if (content.contains("export ")) score += 3;
        if (content.contains("source ")) score += 3;
        if (content.contains("alias ")) score += 3;
        if (content.contains("grep ")) score += 3;
        if (content.contains("awk ")) score += 3;
        if (content.contains("sed ")) score += 3;
        if (content.contains("cat ")) score += 2;
        if (content.contains("ls ")) score += 2;
        if (content.contains("cd ")) score += 2;
        if (content.contains("pwd")) score += 2;
        if (content.contains("mkdir ")) score += 2;
        if (content.contains("rm ")) score += 2;
        if (content.contains("cp ")) score += 2;
        if (content.contains("mv ")) score += 2;
        if (content.contains("chmod ")) score += 3;
        if (content.contains("chown ")) score += 3;
        if (content.contains("sudo ")) score += 3;
        if (content.contains("apt-get") || content.contains("yum ") || content.contains("dnf ") || content.contains("brew ")) score += 4;
        if (content.contains("git ")) score += 3;
        if (content.contains("docker ")) score += 3;
        if (content.contains("kubectl ")) score += 3;
        if (content.contains("$")) score += 1;
        if (content.contains("${")) score += 2;
        if (content.contains("2>&1")) score += 4;
        if (content.contains("/dev/null")) score += 4;
        if (content.contains(" | ")) score += 2;
        if (content.contains(" > ")) score += 2;
        if (content.contains(" >> ")) score += 2;
        
        return score;
    }

    // ===== PowerShell Keywords =====
    private static int calculatePowerShellScore(String content) {
        int score = 0;
        if (content.contains("Write-Host")) score += 5;
        if (content.contains("Write-Output")) score += 5;
        if (content.contains("Get-")) score += 4;
        if (content.contains("Set-")) score += 4;
        if (content.contains("New-")) score += 4;
        if (content.contains("Remove-")) score += 4;
        if (content.contains("Select-Object")) score += 5;
        if (content.contains("Where-Object")) score += 5;
        if (content.contains("ForEach-Object")) score += 5;
        if (content.contains("Sort-Object")) score += 5;
        if (content.contains("Format-Table")) score += 5;
        if (content.contains("Format-List")) score += 5;
        if (content.contains("Invoke-")) score += 4;
        if (content.contains("Test-Path")) score += 5;
        if (content.contains("ConvertFrom-Json")) score += 5;
        if (content.contains("ConvertTo-Json")) score += 5;
        if (content.contains("$")) score += 1;
        if (content.contains("$_.")) score += 4;
        if (content.contains("@(")) score += 3;
        if (content.contains("@{")) score += 3;
        if (content.contains("-eq ")) score += 4;
        if (content.contains("-ne ")) score += 4;
        if (content.contains("-gt ")) score += 4;
        if (content.contains("-lt ")) score += 4;
        if (content.contains("-ge ")) score += 4;
        if (content.contains("-le ")) score += 4;
        if (content.contains("-and ")) score += 4;
        if (content.contains("-or ")) score += 4;
        if (content.contains("-not ")) score += 4;
        if (content.contains("-like ")) score += 4;
        if (content.contains("-match ")) score += 4;
        if (content.contains("function ") && content.contains("{")) score += 2;
        if (content.contains("param(")) score += 4;
        if (content.contains("try {") && content.contains("catch {")) score += 3;
        if (content.contains("throw ")) score += 2;
        if (content.contains("if (") && content.contains(")")) score += 2;
        if (content.contains("elseif (")) score += 3;
        if (content.contains("else {")) score += 2;
        if (content.contains("foreach (")) score += 3;
        if (content.contains("while (")) score += 2;
        if (content.contains("switch (")) score += 2;
        if (content.contains("[string]") || content.contains("[int]") || content.contains("[bool]")) score += 4;
        if (content.contains("[CmdletBinding()]")) score += 5;
        if (content.contains("[Parameter(")) score += 5;
        
        return score;
    }

    // ===== YAML Keywords =====
    private static int calculateYamlScore(String content) {
        int score = 0;
        if (content.contains(":")) score += 1;
        if (content.contains("- ")) score += 1;
        
        // YAML structure checks
        if (content.matches("(?m)^[a-zA-Z0-9_-]+:\\s*$")) score += 3; // key:
        if (content.matches("(?m)^\\s+-[ ]+.*$")) score += 3; // - list item
        if (content.matches("(?m)^\\s+[a-zA-Z0-9_-]+:\\s+.*$")) score += 3; // indented key: value
        
        // Common YAML keys (Kubernetes, CI/CD, etc.)
        if (content.contains("apiVersion:")) score += 5;
        if (content.contains("kind:")) score += 5;
        if (content.contains("metadata:")) score += 5;
        if (content.contains("spec:")) score += 5;
        if (content.contains("containers:")) score += 5;
        if (content.contains("image:")) score += 3;
        if (content.contains("ports:")) score += 3;
        if (content.contains("env:")) score += 3;
        if (content.contains("volumes:")) score += 3;
        if (content.contains("resources:")) score += 3;
        if (content.contains("version:")) score += 3;
        if (content.contains("services:")) score += 3;
        if (content.contains("steps:")) score += 3;
        if (content.contains("jobs:")) score += 3;
        if (content.contains("stages:")) score += 3;
        if (content.contains("variables:")) score += 3;
        if (content.contains("script:")) score += 3;
        if (content.contains("before_script:")) score += 4;
        if (content.contains("after_script:")) score += 4;
        if (content.contains("include:")) score += 3;
        if (content.contains("extends:")) score += 3;
        if (content.contains("true") || content.contains("false")) score += 1;
        if (content.contains("yes") || content.contains("no")) score += 1;
        if (content.contains("on:")) score += 2; // GitHub Actions
        if (content.contains("runs-on:")) score += 4; // GitHub Actions
        
        return score;
    }

    // ===== JSON Keywords =====
    private static int calculateJsonScore(String content) {
        int score = 0;
        String trimmed = content.trim();
        
        // JSON must start with { or [ and end with } or ]
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || 
            (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            score += 5;
        }
        
        if (content.contains("\"") && content.contains(":")) score += 2;
        if (content.contains(":") && (content.contains("{") || content.contains("["))) score += 2;
        if (content.contains("},")) score += 3;
        if (content.contains("],")) score += 3;
        if (content.contains("\": \"")) score += 3;
        if (content.contains("\": ")) score += 2;
        if (content.contains("true") || content.contains("false")) score += 1;
        if (content.contains("null")) score += 1;
        
        // Negative checks - JSON shouldn't have these
        if (content.contains(";") || content.contains("=") || content.contains("(") || content.contains(")")) score -= 5;
        if (content.contains("<") || content.contains(">")) score -= 5;
        if (content.contains("function")) score -= 10;
        if (content.contains("class ")) score -= 10;
        if (content.contains("import ")) score -= 10;
        if (content.contains("var ") || content.contains("let ") || content.contains("const ")) score -= 10;
        
        return score;
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
