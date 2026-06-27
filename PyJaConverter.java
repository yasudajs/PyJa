import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class PyJaConverter {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("エラー: 入力ファイルを指定してください。");
            System.err.println("使用法: java PyJaConverter <ファイル名.pyja>");
            System.exit(1);
        }

        String inputFilePath = args[0];
        if (!inputFilePath.endsWith(".pyja")) {
            System.err.println("エラー: 入力ファイルは .pyja 拡張子である必要があります。");
            System.exit(1);
        }

        String outputFilePath = inputFilePath.substring(0, inputFilePath.length() - 5) + ".java";

        try {
            List<LineInfo> lines = readAndPreprocess(inputFilePath);
            List<String> outputLines = transpile(lines);
            writeOutput(outputFilePath, outputLines);
            System.out.println("変換成功: " + inputFilePath + " -> " + outputFilePath);
        } catch (PyJaException e) {
            System.err.println("コンパイルエラー (" + inputFilePath + ":" + e.getLineNumber() + "): " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("ファイルエラー: " + e.getMessage());
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
        int parenBalance; // この行単体での括弧の増減
        int accumulatedParenBalance; // この行が終わった時点での累積括弧バランス
        
        // 変換用のフラグ・テキスト
        boolean isBlockStart = false;
        String processedText;

        LineInfo(int lineNumber, String originalText) {
            this.lineNumber = lineNumber;
            this.originalText = originalText;
            this.processedText = originalText;
        }
    }

    // カスタム例外クラス
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
                    throw new PyJaException(lineNumber, "タブ文字（\\t）は使用できません。半角スペース4つを使用してください。");
                }

                // インデントサイズの計算
                int spaces = 0;
                while (spaces < lineText.length() && lineText.charAt(spaces) == ' ') {
                    spaces++;
                }
                line.indentSize = spaces;

                // トリミング後の文字列
                String trimmed = lineText.trim();
                line.trimmedText = trimmed;
                
                // トリプルクォートのチェック
                // 簡易的な切り替え判定 (複数行文字列のサポート用)
                if (trimmed.contains("\"\"\"")) {
                    // 行内に奇数個の """ があれば状態を反転
                    int count = countOccurrences(trimmed, "\"\"\"");
                    if (count % 2 != 0) {
                        inTripleQuote = !inTripleQuote;
                    }
                }
                line.inTripleQuote = inTripleQuote;

                // 空行またはコメント行の判定
                line.isEmpty = trimmed.isEmpty();
                line.isComment = trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*");

                // 括弧のバランス計算（リテラルやコメントを除外した簡易判定）
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

    // 2パス目: トランスパイル処理（インデントチェックとJavaコード生成）
    private static List<String> transpile(List<LineInfo> lines) throws PyJaException {
        List<String> output = new ArrayList<>();
        Stack<Integer> indentStack = new Stack<>();
        indentStack.push(0);

        // 有効な行（空行、コメント、括弧の継続行、トリプルクォート内を除く）のインデックスをリスト化
        List<Integer> validLineIndices = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            LineInfo line = lines.get(i);
            
            // 括弧が開いている間の行は、ブロックの開始/終了の判定対象外
            boolean isParenContinued = false;
            if (i > 0) {
                LineInfo prevLine = lines.get(i - 1);
                // 前の行の時点で括弧が開いていれば継続行とみなす
                if (prevLine.accumulatedParenBalance > 0) {
                    isParenContinued = true;
                }
            }

            if (!line.isEmpty && !line.isComment && !line.inTripleQuote && !isParenContinued) {
                validLineIndices.add(i);
            }
        }

        // 各有効行に対してインデント検証とブロック開始判定を行う
        for (int k = 0; k < validLineIndices.size(); k++) {
            int currentIdx = validLineIndices.get(k);
            LineInfo currentLine = lines.get(currentIdx);
            
            int curIndent = currentLine.indentSize;
            
            // インデントが4の倍数であることを確認
            if (curIndent % 4 != 0) {
                throw new PyJaException(currentLine.lineNumber, 
                    "インデントが正しくありません: " + curIndent + " スペース（4の倍数である必要があります）。");
            }

            int prevIndent = indentStack.peek();

            if (curIndent > prevIndent) {
                // インデントが深くなった場合
                if (curIndent - prevIndent > 4) {
                    throw new PyJaException(currentLine.lineNumber, 
                        "インデントが一気に深くなりすぎています。インデントは1レベル（4スペース）ずつ深くしてください。");
                }
                
                // 直前の有効行はブロック開始行のはずであるため、マークする
                if (k > 0) {
                    int prevIdx = validLineIndices.get(k - 1);
                    lines.get(prevIdx).isBlockStart = true;
                }
                indentStack.push(curIndent);

            } else if (curIndent < prevIndent) {
                // インデントが浅くなった場合（デデント）
                // スタックのトップが現在のインデントと一致するまでポップし、対応する `}` を生成用のマークとして処理する
                int popCount = 0;
                while (indentStack.peek() > curIndent) {
                    indentStack.pop();
                    popCount++;
                }

                // 戻り先が過去のインデントレベルと一致しない場合はエラー
                if (indentStack.peek() != curIndent) {
                    throw new PyJaException(currentLine.lineNumber, 
                        "インデントの戻り先（デデント）が不正です。親ブロックのインデントレベルと一致していません。");
                }

                // 現在の有効行の直前に、ポップした数だけの閉じカッコ `}` を挿入するためのマーク
                currentLine.processedText = repeatString(" ", indentStack.peek()) + repeatString("}", popCount) + "\n" + currentLine.originalText;
            }
        }

        // ファイル末尾で残ったインデントレベルをすべて閉じる
        int finalPopCount = 0;
        while (indentStack.peek() > 0) {
            indentStack.pop();
            finalPopCount++;
        }

        // Javaコードへの文法変換
        for (int i = 0; i < lines.size(); i++) {
            LineInfo line = lines.get(i);
            
            if (line.isEmpty || line.isComment || line.inTripleQuote) {
                output.add(line.originalText);
                continue;
            }

            // 前のステップでデデント処理により書き換えられた内容があればそれを使用
            String text = line.processedText;
            
            // 構文の変換 (if, while, for の括弧補完など)
            String convertedTrimmed = convertSyntax(line.trimmedText);

            // ブロック開始行であれば末尾に `{` を追加、そうでなければ必要に応じて `;` を追加
            if (line.isBlockStart) {
                convertedTrimmed = convertedTrimmed + " {";
            } else {
                // セミコロンの自動挿入判定
                // 括弧がすべて閉じられており、セミコロンが必要なステートメントであれば挿入する
                if (line.accumulatedParenBalance == 0 && needsSemicolon(convertedTrimmed)) {
                    convertedTrimmed = convertedTrimmed + ";";
                }
            }

            // インデントを再適用して出力行を作成
            String indentStr = repeatString(" ", line.indentSize);
            
            // すでにデデント用の閉じカッコ `}` が付与されている場合は、インデントの再適用を調整
            if (!text.equals(line.originalText)) {
                // すでに `}` が先頭に追加されている特殊フォーマットなので、
                // processedText内の最終行（元のコード部分）に変換した内容をマッピングする
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

        // ファイル末尾の閉じカッコを追加
        if (finalPopCount > 0) {
            for (int i = finalPopCount - 1; i >= 0; i--) {
                output.add(repeatString("    ", i) + "}");
            }
        }

        return output;
    }

    // Javaの文法構造へ変換 (if, for, while の条件括弧の補完など)
    private static String convertSyntax(String trimmed) {
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

    // セミコロン `;` を付与すべきか判定するルール
    private static boolean needsSemicolon(String trimmed) {
        if (trimmed.isEmpty()) return false;
        
        // すでに記号で終わっている場合は不要
        char lastChar = trimmed.charAt(trimmed.length() - 1);
        if (lastChar == ';' || lastChar == '{' || lastChar == '}') {
            return false;
        }

        // 制御キーワードや定義キーワードで始まっている場合は不要
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
        if (trimmed.startsWith("@")) { // アノテーション
            return false;
        }
        if (trimmed.startsWith("static ") && trimmed.endsWith("{")) { // スタティックブロック
            return false;
        }

        return true;
    }

    // 括弧のバランスを計算する (開き括弧で +1, 閉じ括弧で -1)
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

    // 特定文字列の出現回数をカウント
    private static int countOccurrences(String text, String target) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }

    // 文字列の繰り返し
    private static String repeatString(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    // 変換結果をファイルに書き出す
    private static void writeOutput(String filePath, List<String> lines) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
    }
}
