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

    enum ContextType {
        GLOBAL,
        CLASS_BODY,
        FIELD_SECTION,
        CONST_SECTION,
        METHOD_SECTION,
        METHOD_BODY,
        CONTROL_FLOW
    }

    static class ContextEntry {
        ContextType type;
        int indent;
        int lastSectionOrder = 0; // 1: <field>, 2: <const>, 3: <method>

        ContextEntry(ContextType type, int indent) {
            this.type = type;
            this.indent = indent;
        }
    }

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
        boolean isSectionHeader = false;
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

                String trimmed = lineText.trim();
                // BOM のトリム
                if (lineNumber == 1 && trimmed.startsWith("\uFEFF")) {
                    trimmed = trimmed.substring(1).trim();
                }
                line.trimmedText = trimmed;

                // インデントサイズの計算
                int spaces = 0;
                while (spaces < lineText.length() && lineText.charAt(spaces) == ' ') {
                    spaces++;
                }
                
                // セクションタグの場合、インデントサイズを強制的に 4 にする
                if (trimmed.equals("<field>") || trimmed.equals("<const>") || trimmed.equals("<method>")) {
                    line.indentSize = 4;
                } else {
                    line.indentSize = spaces;
                }

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
        Stack<ContextEntry> contextStack = new Stack<>();
        contextStack.push(new ContextEntry(ContextType.GLOBAL, 0));

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
            String trimmed = currentLine.trimmedText;


            if (curIndent % 4 != 0) {
                throw new PyJaException(currentLine.lineNumber,
                    "Incorrect indentation: " + curIndent + " spaces (must be a multiple of 4).");
            }

            // static の直接使用チェック
            validateNoDirectStatic(currentLine);

            // cls と ins の同時使用チェック
            validateNoDualLevelKeyword(currentLine);

            ContextEntry activeContext = contextStack.peek();

            // インデントが深くなった場合 (ブロックの開始)
            if (curIndent > activeContext.indent) {
                if (curIndent - activeContext.indent > 4) {
                    throw new PyJaException(currentLine.lineNumber,
                        "Indentation increased too much at once. Increase by 1 level (4 spaces) at a time.");
                }

                if (k > 0) {
                    int prevIdx = validLineIndices.get(k - 1);
                    LineInfo prevLine = lines.get(prevIdx);
                    String prevTrimmed = prevLine.trimmedText;

                    ContextType nextType;
                    boolean prevIsBlockStart = true;
                    boolean prevIsSectionHeader = false;
                    boolean prevIsClsBlock = false;

                    if (activeContext.type == ContextType.GLOBAL) {
                        if (isClassDeclaration(prevTrimmed)) {
                            nextType = ContextType.CLASS_BODY;
                        } else {
                            throw new PyJaException(prevLine.lineNumber, "Only class declarations are allowed at the global scope.");
                        }
                    } else if (activeContext.type == ContextType.CLASS_BODY) {
                        if (prevTrimmed.equals("<field>")) {
                            nextType = ContextType.FIELD_SECTION;
                            prevIsBlockStart = false;
                            prevIsSectionHeader = true;
                        } else if (prevTrimmed.equals("<const>")) {
                            nextType = ContextType.CONST_SECTION;
                            prevIsBlockStart = false;
                            prevIsSectionHeader = true;
                        } else if (prevTrimmed.equals("<method>")) {
                            nextType = ContextType.METHOD_SECTION;
                            prevIsBlockStart = false;
                            prevIsSectionHeader = true;
                        } else {
                            throw new PyJaException(prevLine.lineNumber, "Only '<field>', '<const>', or '<method>' sections are allowed inside a class body.");
                        }
                    } else if (activeContext.type == ContextType.FIELD_SECTION) {
                        if (prevTrimmed.equals("cls")) {
                            nextType = ContextType.METHOD_BODY;
                            prevIsClsBlock = true;
                        } else {
                            throw new PyJaException(prevLine.lineNumber, "Only 'cls' block is allowed to start a block in '<field>' section.");
                        }
                    } else if (activeContext.type == ContextType.CONST_SECTION) {
                        if (!hasKeyword(prevTrimmed, "new")) {
                            throw new PyJaException(prevLine.lineNumber, "Constructor declaration requires 'new' keyword.");
                        }
                        nextType = ContextType.METHOD_BODY;
                    } else if (activeContext.type == ContextType.METHOD_SECTION) {
                        if (!hasKeyword(prevTrimmed, "cls") && !hasKeyword(prevTrimmed, "ins")) {
                            throw new PyJaException(prevLine.lineNumber, "Method declaration requires 'cls' or 'ins' keyword.");
                        }
                        nextType = ContextType.METHOD_BODY;
                    } else if (activeContext.type == ContextType.METHOD_BODY || activeContext.type == ContextType.CONTROL_FLOW) {
                        nextType = ContextType.CONTROL_FLOW;
                    } else {
                        throw new PyJaException(prevLine.lineNumber, "Unexpected block start.");
                    }

                    prevLine.isBlockStart = prevIsBlockStart;
                    prevLine.isSectionHeader = prevIsSectionHeader;
                    prevLine.isClsBlock = prevIsClsBlock;

                    contextStack.push(new ContextEntry(nextType, curIndent));
                    activeContext = contextStack.peek();
                }
            } else if (curIndent < activeContext.indent) {
                // インデントが浅くなった場合 (デデント)
                List<Integer> closeIndents = new ArrayList<>();
                while (contextStack.peek().indent > curIndent) {
                    ContextEntry popped = contextStack.pop();
                    if (popped.type == ContextType.CLASS_BODY ||
                        popped.type == ContextType.METHOD_BODY ||
                        popped.type == ContextType.CONTROL_FLOW) {
                        closeIndents.add(popped.indent - 4);
                    }
                }

                if (contextStack.peek().indent != curIndent) {
                    throw new PyJaException(currentLine.lineNumber,
                        "Invalid dedent. Indentation does not match any previous level.");
                }

                if (!closeIndents.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int ind : closeIndents) {
                        sb.append(repeatString(" ", ind)).append("}\n");
                    }
                    currentLine.processedText = sb.toString() + currentLine.originalText;
                }
                activeContext = contextStack.peek();
            }

            // 現在のコンテキストにおけるバリデーションとセクション遷移
            if (activeContext.type == ContextType.CLASS_BODY) {
                if (!trimmed.equals("<field>") && !trimmed.equals("<const>") && !trimmed.equals("<method>")) {
                    throw new PyJaException(currentLine.lineNumber,
                        "'<field>', '<const>', or '<method>' section is required inside a class body.");
                }

                int currentOrder = 0;
                if (trimmed.equals("<field>")) currentOrder = 1;
                else if (trimmed.equals("<const>")) currentOrder = 2;
                else if (trimmed.equals("<method>")) currentOrder = 3;

                if (currentOrder <= activeContext.lastSectionOrder) {
                    throw new PyJaException(currentLine.lineNumber,
                        "Invalid section order. Sections must be in '<field>' -> '<const>' -> '<method>' order, and cannot be duplicated.");
                }
                activeContext.lastSectionOrder = currentOrder;

                ContextType nextSectionType = ContextType.FIELD_SECTION;
                if (trimmed.equals("<const>")) nextSectionType = ContextType.CONST_SECTION;
                else if (trimmed.equals("<method>")) nextSectionType = ContextType.METHOD_SECTION;

                currentLine.isSectionHeader = true;
                contextStack.push(new ContextEntry(nextSectionType, curIndent));
                activeContext = contextStack.peek();
            } else if (activeContext.type == ContextType.FIELD_SECTION ||
                       activeContext.type == ContextType.CONST_SECTION ||
                       activeContext.type == ContextType.METHOD_SECTION) {
                
                // セクション内で別のセクションタグが現れた場合の遷移処理
                if (trimmed.equals("<field>") || trimmed.equals("<const>") || trimmed.equals("<method>")) {
                    contextStack.pop(); // 現在のセクションをポップ
                    activeContext = contextStack.peek(); // CLASS_BODY に戻る

                    // 順序と重複のチェック
                    int currentOrder = 0;
                    if (trimmed.equals("<field>")) currentOrder = 1;
                    else if (trimmed.equals("<const>")) currentOrder = 2;
                    else if (trimmed.equals("<method>")) currentOrder = 3;

                    if (currentOrder <= activeContext.lastSectionOrder) {
                        throw new PyJaException(currentLine.lineNumber,
                            "Invalid section order. Sections must be in '<field>' -> '<const>' -> '<method>' order, and cannot be duplicated.");
                    }
                    activeContext.lastSectionOrder = currentOrder;

                    ContextType nextSectionType = ContextType.FIELD_SECTION;
                    if (trimmed.equals("<const>")) nextSectionType = ContextType.CONST_SECTION;
                    else if (trimmed.equals("<method>")) nextSectionType = ContextType.METHOD_SECTION;

                    // 新しいセクションをプッシュ
                    currentLine.isSectionHeader = true;
                    contextStack.push(new ContextEntry(nextSectionType, curIndent));
                    activeContext = contextStack.peek();
                } else {
                    // 通常行のバリデーション
                    if (activeContext.type == ContextType.FIELD_SECTION) {
                        if (!trimmed.equals("cls")) {
                            if (!hasKeyword(trimmed, "cls") && !hasKeyword(trimmed, "ins")) {
                                throw new PyJaException(currentLine.lineNumber,
                                    "Field declaration requires 'cls' or 'ins' keyword.");
                            }
                        }
                    }
                }
            }
        }

        // ファイル末尾の閉じカッコ処理
        List<Integer> finalCloseIndents = new ArrayList<>();
        while (contextStack.peek().indent > 0) {
            ContextEntry popped = contextStack.pop();
            if (popped.type == ContextType.CLASS_BODY ||
                popped.type == ContextType.METHOD_BODY ||
                popped.type == ContextType.CONTROL_FLOW) {
                finalCloseIndents.add(popped.indent - 4);
            }
        }

        // Java コードへの変換出力
        for (int i = 0; i < lines.size(); i++) {
            LineInfo line = lines.get(i);

            if (line.isEmpty || line.isComment || line.inTripleQuote) {
                output.add(line.originalText);
                continue;
            }

            if (line.isSectionHeader) {
                output.add(""); // セクションヘッダー行は空行にする
                continue;
            }

            String text = line.processedText;
            String convertedTrimmed = convertSyntax(line.trimmedText);

            if (line.isBlockStart) {
                if (line.isClsBlock) {
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

        for (int ind : finalCloseIndents) {
            output.add(repeatString(" ", ind) + "}");
        }

        return output;
    }

    // static の直接使用を禁止
    private static void validateNoDirectStatic(LineInfo line) throws PyJaException {
        String trimmed = line.trimmedText;
        if (trimmed.startsWith("import ")) return;
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
        trimmed = convertLevelKeywords(trimmed);

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

    private static String convertLevelKeywords(String trimmed) {
        String[] tokens = trimmed.split("\\s+", -1);
        List<String> result = new ArrayList<>();
        boolean inModifierRegion = true;

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];

            if (inModifierRegion) {
                if (token.equals("cls")) {
                    result.add("static");
                    continue;
                } else if (token.equals("ins") || (token.equals("new") && isConstructorNew(tokens, i))) {
                    continue;
                } else if (JAVA_MODIFIERS.contains(token) || token.equals("static")) {
                    result.add(token);
                    continue;
                } else {
                    inModifierRegion = false;
                    result.add(token);
                }
            } else {
                result.add(token);
            }
        }

        return String.join(" ", result);
    }

    private static boolean isConstructorNew(String[] tokens, int newIndex) {
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
        if (trimmed.equals("static") || trimmed.equals("static {") || trimmed.equals("cls")) {
            return false;
        }
        if (trimmed.equals("<field>") || trimmed.equals("<const>") || trimmed.equals("<method>")) {
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

    private static boolean hasKeyword(String trimmed, String keyword) {
        String[] tokens = trimmed.split("\\s+");
        for (String token : tokens) {
            if (token.equals(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isClassDeclaration(String trimmed) {
        String[] tokens = trimmed.split("\\s+");
        for (String token : tokens) {
            if (token.equals("class") || token.equals("interface") || token.equals("enum")) {
                return true;
            }
        }
        return false;
    }
}
