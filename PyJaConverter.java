import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

public class PyJaConverter {

    // Java の修飾子キーワード（アクセス修飾子 + その他）
    private static final Set<String> JAVA_MODIFIERS = new HashSet<>(Arrays.asList(
        "public", "private", "protected",
        "abstract", "final", "synchronized", "volatile", "transient", "native", "strictfp", "default"
    ));

    // PyJa のアクセス修飾子
    private static final Set<String> PYJA_ACCESS_MODIFIERS = new HashSet<>(Arrays.asList(
        "public", "private", "family", "home"
    ));

    // PyJa 固有の修飾子キーワード
    private static final Set<String> PYJA_LEVEL_KEYWORDS = new HashSet<>(Arrays.asList("cls", "ins", "new"));

    // 制御構文のキーワード
    private static final Set<String> CONTROL_FLOW_KEYWORDS = new HashSet<>(Arrays.asList(
        "if", "else", "for", "while", "try", "catch", "finally", "switch", "case", "default"
    ));

    // クラス名 -> メンバ定義情報のキャッシュ
    static class ClassInfo {
        Set<String> clsMembers = new HashSet<>();
        Set<String> insMembers = new HashSet<>();
    }
    private static final Map<String, ClassInfo> classCache = new HashMap<>();

    // 現在トランスパイル中のファイル内のインポート文リスト
    private static final List<String> currentImports = new ArrayList<>();

    // 現在のメソッド/クラススコープにおける 変数名 -> 型名 のマッピング
    private static final Map<String, String> variableTypes = new HashMap<>();

    enum ContextType {
        GLOBAL,
        CLASS_BODY,
        FIELD_SECTION,
        CONST_SECTION,
        METHOD_SECTION,
        INNERCLS_SECTION,
        METHOD_BODY,
        CONTROL_FLOW
    }

    static class ContextEntry {
        ContextType type;
        int indent;
        int lastSectionOrder = 0; // 1: <field>, 2: <const>, 3: <method>, 4: <innercls>

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
                line.indentSize = spaces;

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

        currentImports.clear();
        variableTypes.clear();

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

            // インポート文の収集
            if (trimmed.startsWith("import ")) {
                String impPath = trimmed.substring(7).trim();
                if (impPath.endsWith(";")) {
                    impPath = impPath.substring(0, impPath.length() - 1).trim();
                }
                currentImports.add(impPath);
            }

            // アノテーション行の単独行チェック
            if (trimmed.startsWith("@")) {
                validateAnnotationLine(currentLine);
            }

            // static の直接使用チェック
            validateNoDirectStatic(currentLine);

            // cls と ins の同時使用チェック
            validateNoDualLevelKeyword(currentLine);

            // 比較演算子のチェック（import行は除外）
            if (!trimmed.startsWith("import ") && hasInvalidGreaterOperator(trimmed)) {
                throw new PyJaException(currentLine.lineNumber,
                    "Invalid comparison operator '>'. Use '<' or '<=' instead and put the smaller value on the left side.");
            }

            ContextEntry activeContext = contextStack.peek();

            // 1. クラス宣言の次の行で、自動的に CLASS_BODY コンテキストに入る (インデントは変わらない)
            if ((activeContext.type == ContextType.GLOBAL || activeContext.type == ContextType.INNERCLS_SECTION) && k > 0) {
                int prevIdx = validLineIndices.get(k - 1);
                LineInfo prevLine = lines.get(prevIdx);
                if (isClassDeclaration(prevLine.trimmedText)) {
                    contextStack.push(new ContextEntry(ContextType.CLASS_BODY, curIndent + 4));
                    prevLine.isBlockStart = true;
                    activeContext = contextStack.peek();
                }
            }

            // 2. インデントが浅くなった場合 (デデント)
            int targetIndent = curIndent;
            boolean isSectionTag = trimmed.equals("<field>") || trimmed.equals("<const>") || trimmed.equals("<method>") || trimmed.equals("<innercls>");
            if (isSectionTag) {
                int classIndent = 4;
                for (int i = contextStack.size() - 1; i >= 0; i--) {
                    if (contextStack.get(i).type == ContextType.CLASS_BODY) {
                        classIndent = contextStack.get(i).indent;
                        break;
                    }
                }
                targetIndent = classIndent;
            }

            if (targetIndent < activeContext.indent) {
                List<Integer> closeIndents = new ArrayList<>();
                while (contextStack.peek().indent > targetIndent) {
                    ContextEntry popped = contextStack.pop();
                    if (popped.type == ContextType.CLASS_BODY ||
                        popped.type == ContextType.METHOD_BODY ||
                        popped.type == ContextType.CONTROL_FLOW) {
                        closeIndents.add(popped.indent - 4);
                    }
                }

                if (contextStack.peek().indent != targetIndent) {
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

            // 3. セクションタグによるコンテキスト遷移 (インデント判定やデデントの後に処理し、インデントを引き継ぐ)
            if (isSectionTag) {
                currentLine.isSectionHeader = true;

                if (activeContext.type != ContextType.CLASS_BODY &&
                    activeContext.type != ContextType.FIELD_SECTION &&
                    activeContext.type != ContextType.CONST_SECTION &&
                    activeContext.type != ContextType.METHOD_SECTION &&
                    activeContext.type != ContextType.INNERCLS_SECTION) {
                    throw new PyJaException(currentLine.lineNumber,
                        "Section tags can only be declared directly inside a class body.");
                }

                // 既存のセクションがある場合は一旦ポップ
                int inheritedIndent = activeContext.indent;
                if (activeContext.type == ContextType.FIELD_SECTION ||
                    activeContext.type == ContextType.CONST_SECTION ||
                    activeContext.type == ContextType.METHOD_SECTION ||
                    activeContext.type == ContextType.INNERCLS_SECTION) {
                    contextStack.pop();
                    activeContext = contextStack.peek();
                }

                if (activeContext.type == ContextType.CLASS_BODY) {
                    inheritedIndent = activeContext.indent;
                } else {
                    throw new PyJaException(currentLine.lineNumber,
                        "Section tags must be declared directly inside a class body.");
                }

                // 順序と重複のチェック
                int currentOrder = 0;
                if (trimmed.equals("<field>")) currentOrder = 1;
                else if (trimmed.equals("<const>")) currentOrder = 2;
                else if (trimmed.equals("<method>")) currentOrder = 3;
                else if (trimmed.equals("<innercls>")) currentOrder = 4;

                if (currentOrder <= activeContext.lastSectionOrder) {
                    throw new PyJaException(currentLine.lineNumber,
                        "Invalid section order. Sections must be in '<field>' -> '<const>' -> '<method>' -> '<innercls>' order, and cannot be duplicated.");
                }
                activeContext.lastSectionOrder = currentOrder;

                ContextType nextSectionType = ContextType.FIELD_SECTION;
                if (trimmed.equals("<const>")) nextSectionType = ContextType.CONST_SECTION;
                else if (trimmed.equals("<method>")) nextSectionType = ContextType.METHOD_SECTION;
                else if (trimmed.equals("<innercls>")) nextSectionType = ContextType.INNERCLS_SECTION;

                // 新しいセクションをプッシュ (引き継いだインデントを使用)
                contextStack.push(new ContextEntry(nextSectionType, inheritedIndent));
                activeContext = contextStack.peek();
                continue;
            }

            // 4. インデントが深くなった場合 (ブロックの開始)
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
                        throw new PyJaException(prevLine.lineNumber, "Members must be declared inside a section (<field>, <const>, <method>, or <innercls>).");
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
                    } else if (activeContext.type == ContextType.INNERCLS_SECTION) {
                        if (!isClassDeclaration(prevTrimmed)) {
                            throw new PyJaException(prevLine.lineNumber, "Only class, interface, or enum declarations are allowed inside '<innercls>' section.");
                        }
                        nextType = ContextType.CLASS_BODY;
                    } else if (activeContext.type == ContextType.METHOD_BODY || activeContext.type == ContextType.CONTROL_FLOW) {
                        if (!isControlFlowStatement(prevTrimmed)) {
                            throw new PyJaException(prevLine.lineNumber,
                                "Indentation can only be increased after control flow statements (if, else, for, while, try, catch, finally, switch).");
                        }
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
            }

            // 簡易型トラッキングの更新
            if (activeContext.type == ContextType.FIELD_SECTION) {
                trackFieldDeclaration(trimmed);
            } else if (activeContext.type == ContextType.CONST_SECTION || activeContext.type == ContextType.METHOD_SECTION) {
                if (isMethodDeclaration(trimmed) || hasKeyword(trimmed, "new")) {
                    trackMethodArguments(trimmed);
                }
            } else if (activeContext.type == ContextType.METHOD_BODY || activeContext.type == ContextType.CONTROL_FLOW) {
                trackLocalDeclaration(trimmed);
            }

            // インスタンス経由の cls メンバーアクセス制限チェック
            validateInstanceClsAccess(currentLine, inputFilePath);

            // アクセス修飾子必須のバリデーション
            boolean requiresAccessModifier = false;
            if (activeContext.type == ContextType.GLOBAL || activeContext.type == ContextType.INNERCLS_SECTION) {
                if (isClassDeclaration(trimmed)) {
                    requiresAccessModifier = true;
                }
            } else if (activeContext.type == ContextType.FIELD_SECTION) {
                if (!trimmed.equals("cls")) {
                    requiresAccessModifier = true;
                }
            } else if (activeContext.type == ContextType.CONST_SECTION) {
                if (isMethodDeclaration(trimmed) || hasKeyword(trimmed, "new")) {
                    requiresAccessModifier = true;
                }
            } else if (activeContext.type == ContextType.METHOD_SECTION) {
                if (isMethodDeclaration(trimmed)) {
                    requiresAccessModifier = true;
                }
            }

            if (requiresAccessModifier && !trimmed.startsWith("@")) {
                String[] tokens = trimmed.split("\\s+");
                if (tokens.length == 0 || !PYJA_ACCESS_MODIFIERS.contains(tokens[0])) {
                    throw new PyJaException(currentLine.lineNumber,
                        "Access modifier (public, private, family, home) is required at the start of the declaration.");
                }
            }

            // 5. 現在のコンテキストにおけるバリデーション
            if (activeContext.type == ContextType.CLASS_BODY) {
                throw new PyJaException(currentLine.lineNumber,
                    "'<field>', '<const>', '<method>', or '<innercls>' section is required before declaring members.");
            } else if (activeContext.type == ContextType.FIELD_SECTION) {
                if (!trimmed.equals("cls")) {
                    if (!hasKeyword(trimmed, "cls") && !hasKeyword(trimmed, "ins")) {
                        throw new PyJaException(currentLine.lineNumber,
                            "Field declaration requires 'cls' or 'ins' keyword.");
                    }
                    if (isMethodDeclaration(trimmed)) {
                        throw new PyJaException(currentLine.lineNumber,
                            "Method declarations are not allowed inside '<field>' section.");
                    }
                    if (!isValidFieldDeclaration(trimmed)) {
                        throw new PyJaException(currentLine.lineNumber,
                            "Invalid field declaration. Type and variable name are required.");
                    }
                }
            } else if (activeContext.type == ContextType.METHOD_SECTION) {
                if (!isMethodDeclaration(trimmed)) {
                    throw new PyJaException(currentLine.lineNumber,
                        "Only method declarations are allowed inside '<method>' section.");
                }
            } else if (activeContext.type == ContextType.INNERCLS_SECTION) {
                if (!isClassDeclaration(trimmed)) {
                    throw new PyJaException(currentLine.lineNumber,
                        "Only class, interface, or enum declarations are allowed inside '<innercls>' section.");
                }
                if (!hasKeyword(trimmed, "cls") && !hasKeyword(trimmed, "ins")) {
                    throw new PyJaException(currentLine.lineNumber,
                        "Inner class, interface, or enum declaration requires 'cls' or 'ins' keyword.");
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
                if (!line.processedText.equals(line.originalText)) {
                    String[] parts = line.processedText.split("\n", -1);
                    for (int j = 0; j < parts.length - 1; j++) {
                        output.add(parts[j]);
                    }
                }
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
                    int commentIdx = convertedTrimmed.indexOf("//");
                    if (commentIdx != -1) {
                        String stmt = convertedTrimmed.substring(0, commentIdx).trim();
                        String comment = convertedTrimmed.substring(commentIdx);
                        convertedTrimmed = stmt + ";" + (comment.isEmpty() ? "" : " " + comment);
                    } else {
                        convertedTrimmed = convertedTrimmed + ";";
                    }
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
                } else if (token.equals("family")) {
                    result.add("protected");
                    continue;
                } else if (token.equals("home")) {
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
            if (!JAVA_MODIFIERS.contains(tokens[i]) && !PYJA_LEVEL_KEYWORDS.contains(tokens[i]) && !PYJA_ACCESS_MODIFIERS.contains(tokens[i])) {
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

        if (isClassDeclaration(trimmed)) {
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
        if (trimmed.equals("<field>") || trimmed.equals("<const>") || trimmed.equals("<method>") || trimmed.equals("<innercls>")) {
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

    private static boolean isMethodDeclaration(String trimmed) {
        int parenIdx = trimmed.indexOf('(');
        int equalIdx = trimmed.indexOf('=');

        if (parenIdx == -1) {
            return false;
        }

        if (equalIdx != -1 && equalIdx < parenIdx) {
            return false;
        }

        if (trimmed.startsWith("if ") || trimmed.startsWith("for ") || trimmed.startsWith("while ") || trimmed.startsWith("catch ") || trimmed.startsWith("else if ")) {
            return false;
        }
        if (isClassDeclaration(trimmed)) {
            return false;
        }

        return true;
    }

    private static boolean isValidFieldDeclaration(String trimmed) {
        if (trimmed.equals("cls")) return true;

        String[] tokens = trimmed.split("\\s+");
        List<String> remainingTokens = new ArrayList<>();
        for (String token : tokens) {
            if (!JAVA_MODIFIERS.contains(token) && !PYJA_LEVEL_KEYWORDS.contains(token) && !token.equals("static") && !PYJA_ACCESS_MODIFIERS.contains(token)) {
                remainingTokens.add(token);
            }
        }

        if (remainingTokens.isEmpty()) return false;

        String remaining = String.join(" ", remainingTokens);
        String leftSide = remaining;
        int equalIdx = remaining.indexOf('=');
        if (equalIdx != -1) {
            leftSide = remaining.substring(0, equalIdx).trim();
        }

        String[] leftTokens = leftSide.split("\\s+");
        return leftTokens.length >= 2;
    }

    private static boolean isControlFlowStatement(String trimmed) {
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length == 0) return false;

        String firstWord = tokens[0];
        if (firstWord.equals("else")) {
            return true;
        }

        return CONTROL_FLOW_KEYWORDS.contains(firstWord);
    }

    private static boolean hasInvalidGreaterOperator(String trimmed) {
        String temp = trimmed.replace("->", "  ");
        temp = temp.replace(">>>", "   ").replace(">>", "  ");

        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < temp.length(); i++) {
            char c = temp.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                if (depth > 0) {
                    depth--;
                } else {
                    sb.append(c);
                }
            } else {
                if (depth == 0) {
                    sb.append(c);
                }
            }
        }
        return sb.toString().contains(">");
    }

    // --- オンデマンド型解決用ヘルパーメソッド ---

    private static String extractClsMemberName(String trimmed) {
        if (trimmed.equals("cls") || trimmed.equals("ins")) return null;

        String[] tokens = trimmed.split("\\s+");
        int clsIdx = -1;
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].equals("cls") || tokens[i].equals("ins")) {
                clsIdx = i;
                break;
            }
        }

        if (clsIdx == -1 || clsIdx == tokens.length - 1) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = clsIdx + 1; i < tokens.length; i++) {
            sb.append(tokens[i]).append(" ");
        }
        String remaining = sb.toString().trim();

        int equalIdx = remaining.indexOf('=');
        String namePart = remaining;
        if (equalIdx != -1) {
            namePart = remaining.substring(0, equalIdx).trim();
        }

        int parenIdx = namePart.indexOf('(');
        if (parenIdx != -1) {
            namePart = namePart.substring(0, parenIdx).trim();
        }

        String[] nameTokens = namePart.split("\\s+");
        if (nameTokens.length > 0) {
            return nameTokens[nameTokens.length - 1];
        }
        return null;
    }

    private static ClassInfo findClassInfo(String className, String currentFilePath) {
        if (classCache.containsKey(className)) {
            return classCache.get(className);
        }

        File pyjaFile = findPyJaFile(className, currentFilePath);
        if (pyjaFile == null || !pyjaFile.exists()) {
            return null;
        }

        ClassInfo info = new ClassInfo();
        try (BufferedReader reader = new BufferedReader(new FileReader(pyjaFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                    continue;
                }

                if (trimmed.contains(" cls ")) {
                    String name = extractClsMemberName(trimmed);
                    if (name != null) {
                        info.clsMembers.add(name);
                    }
                }
                if (trimmed.contains(" ins ")) {
                    String name = extractClsMemberName(trimmed);
                    if (name != null) {
                        info.insMembers.add(name);
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }

        classCache.put(className, info);
        return info;
    }

    private static File findPyJaFile(String className, String currentFilePath) {
        File currentFile = new File(currentFilePath).getAbsoluteFile();
        File parentDir = currentFile.getParentFile();

        // 1. 同一ディレクトリから探す
        if (parentDir != null) {
            File sameDirFile = new File(parentDir, className + ".pyja");
            if (sameDirFile.exists()) {
                return sameDirFile;
            }
        }

        // 2. インポート文から探す
        for (String imp : currentImports) {
            if (imp.endsWith("." + className)) {
                String relativePath = imp.replace('.', File.separatorChar) + ".pyja";
                File impFile = new File(parentDir, relativePath);
                if (impFile.exists()) {
                    return impFile;
                }
            }
        }

        return null;
    }

    private static void trackFieldDeclaration(String trimmed) {
        if (trimmed.equals("cls")) return;

        String[] tokens = trimmed.split("\\s+");
        List<String> remaining = new ArrayList<>();
        for (String t : tokens) {
            if (!JAVA_MODIFIERS.contains(t) && !PYJA_LEVEL_KEYWORDS.contains(t) && !t.equals("static") && !PYJA_ACCESS_MODIFIERS.contains(t)) {
                remaining.add(t);
            }
        }
        if (remaining.isEmpty()) return;

        String decl = String.join(" ", remaining);
        int equalIdx = decl.indexOf('=');
        if (equalIdx != -1) {
            decl = decl.substring(0, equalIdx).trim();
        }

        String[] declTokens = decl.split("\\s+");
        if (declTokens.length >= 2) {
            String type = declTokens[declTokens.length - 2];
            String name = declTokens[declTokens.length - 1];
            int genIdx = type.indexOf('<');
            if (genIdx != -1) {
                type = type.substring(0, genIdx).trim();
            }
            variableTypes.put(name, type);
        }
    }

    private static void trackMethodArguments(String trimmed) {
        int parenStart = trimmed.indexOf('(');
        int parenEnd = trimmed.lastIndexOf(')');
        if (parenStart == -1 || parenEnd == -1 || parenEnd <= parenStart) {
            return;
        }
        String argsStr = trimmed.substring(parenStart + 1, parenEnd).trim();
        if (argsStr.isEmpty()) return;

        String[] args = argsStr.split(",");
        for (String arg : args) {
            String[] tokens = arg.trim().split("\\s+");
            if (tokens.length >= 2) {
                String type = tokens[tokens.length - 2];
                String name = tokens[tokens.length - 1];
                int genIdx = type.indexOf('<');
                if (genIdx != -1) {
                    type = type.substring(0, genIdx).trim();
                }
                variableTypes.put(name, type);
            }
        }
    }

    private static void trackLocalDeclaration(String trimmed) {
        String left = trimmed;
        int equalIdx = trimmed.indexOf('=');
        if (equalIdx != -1) {
            left = trimmed.substring(0, equalIdx).trim();
        }

        String[] tokens = left.split("\\s+");
        if (tokens.length == 2) {
            String type = tokens[0];
            String name = tokens[1];

            if (Character.isUpperCase(type.charAt(0)) ||
                Arrays.asList("int", "double", "float", "long", "short", "byte", "char", "boolean").contains(type) ||
                type.contains("<")) {
                int genIdx = type.indexOf('<');
                if (genIdx != -1) {
                    type = type.substring(0, genIdx).trim();
                }
                variableTypes.put(name, type);
            }
        }
    }

    private static void validateInstanceClsAccess(LineInfo line, String currentFilePath) throws PyJaException {
        String trimmed = line.trimmedText;
        if (trimmed.startsWith("import ")) return;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b([a-z_][a-zA-Z0-9_]*)\\.([a-zA-Z0-9_]+)\\b");
        java.util.regex.Matcher matcher = pattern.matcher(trimmed);

        while (matcher.find()) {
            String varName = matcher.group(1);
            String memberName = matcher.group(2);

            if (variableTypes.containsKey(varName)) {
                String typeName = variableTypes.get(varName);
                ClassInfo info = findClassInfo(typeName, currentFilePath);
                if (info != null && info.clsMembers.contains(memberName)) {
                    throw new PyJaException(line.lineNumber,
                        "Cannot access class (cls) member '" + memberName + "' via an instance '" + varName + "'. Use class name '" + typeName + "' instead.");
                }
            }
        }
    }

    private static void validateAnnotationLine(LineInfo line) throws PyJaException {
        String trimmed = line.trimmedText;
        if (!trimmed.startsWith("@")) return;

        // アノテーション名部分（@の直後から、英数字・ドット・アンダースコアが続く部分）を取得
        int idx = 1;
        while (idx < trimmed.length()) {
            char c = trimmed.charAt(idx);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                idx++;
            } else {
                break;
            }
        }

        // アノテーション名の直後にある文字を調べる（スペースはスキップ）
        int nextCharIdx = idx;
        while (nextCharIdx < trimmed.length() && Character.isWhitespace(trimmed.charAt(nextCharIdx))) {
            nextCharIdx++;
        }

        if (nextCharIdx == trimmed.length()) {
            // アノテーション名だけで行が終わっている（例: @Override） -> OK
            return;
        }

        char nextChar = trimmed.charAt(nextCharIdx);
        if (nextChar == '(') {
            // 括弧付きのアノテーション（例: @SuppressWarnings("unchecked")）
            // 対応する閉じ括弧を探す
            int balance = 0;
            int closeParenIdx = -1;
            boolean inString = false;
            boolean escape = false;
            for (int i = nextCharIdx; i < trimmed.length(); i++) {
                char c = trimmed.charAt(i);
                if (escape) { escape = false; continue; }
                if (c == '\\') { escape = true; continue; }
                if (c == '"') { inString = !inString; continue; }
                if (!inString) {
                    if (c == '(') balance++;
                    else if (c == ')') {
                        balance--;
                        if (balance == 0) {
                            closeParenIdx = i;
                            break;
                        }
                    }
                }
            }
            if (closeParenIdx == -1) {
                throw new PyJaException(line.lineNumber, "Mismatched parentheses in annotation.");
            }
            // 閉じ括弧の後にスペース以外の文字があればエラー
            String trailing = trimmed.substring(closeParenIdx + 1).trim();
            if (!trailing.isEmpty()) {
                throw new PyJaException(line.lineNumber, 
                    "Annotation must be written on a single line. Do not write code after the annotation on the same line.");
            }
        } else {
            // 括弧がないアノテーション（例: @Override）なのに、直後に何か文字がある -> エラー
            throw new PyJaException(line.lineNumber, 
                "Annotation must be written on a single line. Do not write code after the annotation on the same line.");
        }
    }
}
