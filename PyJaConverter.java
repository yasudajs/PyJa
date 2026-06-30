import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

public class PyJaConverter {

    // Java の修飾子キーワード（アクセス修飾子 + その他）
    private static final Set<String> JAVA_MODIFIERS = new HashSet<>(Arrays.asList(
        "public", "private", "protected",
        "abstract", "final", "synchronized", "volatile", "transient", "native", "strictfp", "default"
    ));

    // PyJa 固有の修飾子キーワード
    private static final Set<String> PYJA_LEVEL_KEYWORDS = new HashSet<>(Arrays.asList("cls", "ins", "new"));

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Error: Please specify an input file.");
            System.err.println("Usage: java PyJaConverter <filename.pyja>");
            System.exit(1);
        }

        String inputFilePath = args[0];
        if (!inputFilePath.endsWith(".pyja")) {
            System.err.println("Error: Input file must have a .pyja extension.");
            System.exit(1);
        }

        String outputFilePath = inputFilePath.substring(0, inputFilePath.length() - 5) + ".java";

        try {
            List<LineInfo> lines = readAndPreprocess(inputFilePath);
            List<String> outputLines = transpile(lines, inputFilePath);
            writeOutput(outputFilePath, outputLines);
            System.out.println("Success: " + inputFilePath + " -> " + outputFilePath);
        } catch (PyJaException e) {
            System.err.println("Compile error (" + inputFilePath + ":" + e.getLineNumber() + "): " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("File error: " + e.getMessage());
            System.exit(1);
        }
    }

    static class LineInfo {
        int lineNumber;
        String originalText;
        int indentSize;
        String trimmedText;
        boolean isEmpty;
        boolean isComment;
        boolean inTripleQuote;
        int parenBalance;
        int accumulatedParenBalance;

        boolean isBlockStart = false;
        boolean isClsBlock = false;  // cls 単独ブロック（静的初期化ブロック）
        String processedText;

        LineInfo(int lineNumber, String originalText) {
            this.lineNumber = lineNumber;
            this.originalText = originalText;
            this.processedText = originalText;
        }
    }

    static class PyJaException extends Exception {
        private final int lineNumber;

        public PyJaException(int lineNumber, String message) {
            super(message);
            this.lineNumber = lineNumber;
        }

        public int getLineNumber() {
            return lineNumber;
        }
    }

    // 1パス目: 行の読み込みとメタデータの解析
    private static List<LineInfo> readAndPreprocess(String filePath) throws IOException, PyJaException {
        List<LineInfo> lines = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String lineText;
            int lineNumber = 0;
            boolean inTripleQuote = false;
            int currentParenBalance = 0;

            while ((lineText = reader.readLine()) != null) {
                lineNumber++;
                LineInfo line = new LineInfo(lineNumber, lineText);

                // タブ文字のチェック
                if (lineText.contains("\t")) {
                    throw new PyJaException(lineNumber, "Tab characters are not allowed. Use 4 spaces for indentation.");
                }

                // インデントサイズの計算
                int spaces = 0;
                while (spaces < lineText.length() && lineText.charAt(spaces) == ' ') {
                    spaces++;
                }
                line.indentSize = spaces;

                String trimmed = lineText.trim();
                line.trimmedText = trimmed;

                // トリプルクォートのチェック
                if (trimmed.contains("\"\"\"")) {
                    int count = countOccurrences(trimmed, "\"\"\"");
                    if (count % 2 != 0) {
                        inTripleQuote = !inTripleQuote;
                    }
                }
                line.inTripleQuote = inTripleQuote;

                line.isEmpty = trimmed.isEmpty();
                line.isComment = trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*");

                if (!line.isEmpty && !line.isComment && !line.inTripleQuote) {
                    int bal = calculateParenBalance(trimmed);
                    line.parenBalance = bal;
                    currentParenBalance += bal;
                }
                line.accumulatedParenBalance = currentParenBalance;

                lines.add(line);
            }
        }
        return lines;
    }

    // 2パス目: トランスパイル処理
    private static List<String> transpile(List<LineInfo> lines, String inputFilePath) throws PyJaException {
        List<String> output = new ArrayList<>();
        Stack<Integer> indentStack = new Stack<>();
        indentStack.push(0);

        List<Integer> validLineIndices = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            LineInfo line = lines.get(i);

            boolean isParenContinued = false;
            if (i > 0) {
                LineInfo prevLine = lines.get(i - 1);
                if (prevLine.accumulatedParenBalance > 0) {
                    isParenContinued = true;
                }
            }

            if (!line.isEmpty && !line.isComment && !line.inTripleQuote && !isParenContinued) {
                validLineIndices.add(i);
            }
        }

        for (int k = 0; k < validLineIndices.size(); k++) {
            int currentIdx = validLineIndices.get(k);
            LineInfo currentLine = lines.get(currentIdx);

            int curIndent = currentLine.indentSize;

            if (curIndent % 4 != 0) {
                throw new PyJaException(currentLine.lineNumber,
                    "Incorrect indentation: " + curIndent + " spaces (must be a multiple of 4).");
            }

            // static の直接使用チェック
            validateNoDirectStatic(currentLine);

            // cls と ins の同時使用チェック
            validateNoDualLevelKeyword(currentLine);

            int prevIndent = indentStack.peek();

            if (curIndent > prevIndent) {
                if (curIndent - prevIndent > 4) {
                    throw new PyJaException(currentLine.lineNumber,
                        "Indentation increased too much at once. Increase by 1 level (4 spaces) at a time.");
                }

                if (k > 0) {
                    int prevIdx = validLineIndices.get(k - 1);
                    LineInfo prevLine = lines.get(prevIdx);
                    prevLine.isBlockStart = true;
                    // cls 単独行（"cls" だけ）の検出
                    if (prevLine.trimmedText.equals("cls")) {
                        prevLine.isClsBlock = true;
                    }
                }
                indentStack.push(curIndent);

            } else if (curIndent < prevIndent) {
                int popCount = 0;
                while (indentStack.peek() > curIndent) {
                    indentStack.pop();
                    popCount++;
                }

                if (indentStack.peek() != curIndent) {
                    throw new PyJaException(currentLine.lineNumber,
                        "Invalid dedent. Indentation does not match any previous level.");
                }

                currentLine.processedText = repeatString(" ", indentStack.peek()) + repeatString("}", popCount) + "\n" + currentLine.originalText;
            }
        }

        // ファイル末尾の閉じカッコ処理
        int finalPopCount = 0;
        while (indentStack.peek() > 0) {
            indentStack.pop();
            finalPopCount++;
        }

        // Java コードへの変換出力
        for (int i = 0; i < lines.size(); i++) {
            LineInfo line = lines.get(i);

            if (line.isEmpty || line.isComment || line.inTripleQuote) {
                output.add(line.originalText);
                continue;
            }

            String text = line.processedText;
            String convertedTrimmed = convertSyntax(line.trimmedText);

            if (line.isBlockStart) {
                if (line.isClsBlock) {
                    // cls 単独ブロック → static {
                    convertedTrimmed = "static {";
                } else {
                    convertedTrimmed = convertedTrimmed + " {";
                }
            } else {
                if (line.accumulatedParenBalance == 0 && needsSemicolon(convertedTrimmed)) {
                    convertedTrimmed = convertedTrimmed + ";";
                }
            }

            String indentStr = repeatString(" ", line.indentSize);

            if (!text.equals(line.originalText)) {
                String[] parts = text.split("\n", -1);
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < parts.length - 1; j++) {
                    sb.append(parts[j]).append("\n");
                }
                sb.append(indentStr).append(convertedTrimmed);
                output.add(sb.toString());
            } else {
                output.add(indentStr + convertedTrimmed);
            }
        }

        if (finalPopCount > 0) {
            for (int i = finalPopCount - 1; i >= 0; i--) {
                output.add(repeatString("    ", i) + "}");
            }
        }

        return output;
    }

    // static の直接使用を禁止
    private static void validateNoDirectStatic(LineInfo line) throws PyJaException {
        String trimmed = line.trimmedText;
        // import static はOK（Javaの static import）
        if (trimmed.startsWith("import ")) return;
        // トークン分割して "static" が含まれているかチェック
        String[] tokens = trimmed.split("\\s+");
        for (String token : tokens) {
            if (token.equals("static")) {
                throw new PyJaException(line.lineNumber,
                    "'static' cannot be used directly in PyJa. Use 'cls' instead.");
            }
        }
    }

    // cls と ins の同時使用を禁止
    private static void validateNoDualLevelKeyword(LineInfo line) throws PyJaException {
        String trimmed = line.trimmedText;
        String[] tokens = trimmed.split("\\s+");
        boolean hasCls = false;
        boolean hasIns = false;
        boolean hasNew = false;
        for (String token : tokens) {
            if (token.equals("cls")) hasCls = true;
            if (token.equals("ins")) hasIns = true;
            if (token.equals("new")) hasNew = true;
        }
        int count = (hasCls ? 1 : 0) + (hasIns ? 1 : 0) + (hasNew ? 1 : 0);
        if (count > 1) {
            throw new PyJaException(line.lineNumber,
                "Cannot use 'cls', 'ins', and 'new' in combination. Use only one.");
        }
    }

    // Java 構文への変換
    private static String convertSyntax(String trimmed) {
        // cls / ins / new の修飾子変換
        trimmed = convertLevelKeywords(trimmed);

        // if / else if / while / for / catch の括弧補完
        if (trimmed.startsWith("if ") && !trimmed.startsWith("if (")) {
            String condition = trimmed.substring(3).trim();
            return "if (" + condition + ")";
        }
        if (trimmed.startsWith("else if ") && !trimmed.startsWith("else if (")) {
            String condition = trimmed.substring(8).trim();
            return "else if (" + condition + ")";
        }
        if (trimmed.startsWith("while ") && !trimmed.startsWith("while (")) {
            String condition = trimmed.substring(6).trim();
            return "while (" + condition + ")";
        }
        if (trimmed.startsWith("for ") && !trimmed.startsWith("for (")) {
            String loopExpr = trimmed.substring(4).trim();
            return "for (" + loopExpr + ")";
        }
        if (trimmed.startsWith("catch ") && !trimmed.startsWith("catch (")) {
            String catchExpr = trimmed.substring(6).trim();
            return "catch (" + catchExpr + ")";
        }
        return trimmed;
    }

    /**
     * PyJa の cls / ins / new キーワードを Java の修飾子に変換する。
     *
     * 変換ルール:
     *   cls → static（に置換）
     *   ins → 削除
     *   new → 削除
     *
     * トークンを左から右にスキャンし、修飾子位置に現れた場合のみ変換する。
     * 修飾子位置とは: public/private/protected やその他修飾子が続く位置。
     */
    private static String convertLevelKeywords(String trimmed) {
        String[] tokens = trimmed.split("\\s+", -1);
        List<String> result = new ArrayList<>();
        boolean inModifierRegion = true; // 先頭から修飾子が続く領域

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];

            if (inModifierRegion) {
                if (token.equals("cls")) {
                    result.add("static");
                    continue;
                } else if (token.equals("ins") || (token.equals("new") && isConstructorNew(tokens, i))) {
                    // ins は削除、new がコンストラクタ文脈なら削除
                    continue;
                } else if (JAVA_MODIFIERS.contains(token) || token.equals("static")) {
                    result.add(token);
                    continue;
                } else {
                    // 修飾子でないトークンが来たら修飾子領域を抜ける
                    inModifierRegion = false;
                    result.add(token);
                }
            } else {
                result.add(token);
            }
        }

        return String.join(" ", result);
    }

    /**
     * "new" トークンがコンストラクタ宣言の修飾子かどうかを判定する。
     * コンストラクタ宣言の文脈: 修飾子列の中にある "new" であること。
     * 式の中の new（例: new Counter()）は inModifierRegion=false の後に来るため除外される。
     */
    private static boolean isConstructorNew(String[] tokens, int newIndex) {
        // newIndex より前のトークンが全て修飾子であれば、コンストラクタ宣言の new
        for (int i = 0; i < newIndex; i++) {
            if (!JAVA_MODIFIERS.contains(tokens[i]) && !PYJA_LEVEL_KEYWORDS.contains(tokens[i])) {
                return false;
            }
        }
        return true;
    }

    // セミコロンを付与すべきか判定
    private static boolean needsSemicolon(String trimmed) {
        if (trimmed.isEmpty()) return false;

        char lastChar = trimmed.charAt(trimmed.length() - 1);
        if (lastChar == ';' || lastChar == '{' || lastChar == '}') {
            return false;
        }

        if (trimmed.startsWith("class ") || trimmed.startsWith("interface ") || trimmed.startsWith("enum ")) {
            return false;
        }
        if (trimmed.startsWith("if ") || trimmed.equals("else") || trimmed.startsWith("else ") ||
            trimmed.startsWith("while ") || trimmed.startsWith("for ") || trimmed.startsWith("switch ")) {
            return false;
        }
        if (trimmed.startsWith("try") || trimmed.startsWith("catch ") || trimmed.equals("finally") || trimmed.startsWith("finally ")) {
            return false;
        }
        if (trimmed.startsWith("@")) {
            return false;
        }
        // cls ブロック / static ブロック
        if (trimmed.equals("static") || trimmed.equals("static {") || trimmed.equals("cls")) {
            return false;
        }

        return true;
    }

    private static int calculateParenBalance(String text) {
        int balance = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escape) {
                escape = false;
                continue;
            }

            if (c == '\\') {
                escape = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '(' || c == '[' || c == '{') {
                    balance++;
                } else if (c == ')' || c == ']' || c == '}') {
                    balance--;
                }
            }
        }
        return balance;
    }

    private static int countOccurrences(String text, String target) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }

    private static String repeatString(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    private static void writeOutput(String filePath, List<String> lines) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
    }
}
