# 例外処理

PyJaでは、Javaと同様に例外処理（`try-catch-finally` および `try-with-resources`）をサポートしていますが、記述をシンプルにするために丸括弧や中括弧を一部省略できます。

---

## 1. try-catch-finally

中括弧 `{ }` によるブロック表現は不要です。また、`catch` に続く例外定義の丸括弧 `( )` は省略可能です。

```java
try
    System.out.println("処理を開始します。")
    int result = 10 / 0
    System.out.print(result)
catch ArithmeticException e
    System.out.println("ゼロ除算例外をキャッチ: " + e.getMessage())
catch Exception e
    System.out.println("その他の例外をキャッチ: " + e.getMessage())
finally
    System.out.println("常に実行されるfinallyブロック")
```
* 関連コード: [SampleTryCatchFinally.pyja](../../tests/SampleTryCatchFinally.pyja)

---

## 2. try-with-resources

リソース（ファイルやストリーム等）を自動的にクローズするための `try-with-resources` 構文もサポートしています。

### 記述ルール
* `try` の直下の行（インデントなし）に開始の丸括弧 `(` を記述します。
* 各リソース宣言を記述します。末尾のセミコロン `;` は省略可能です。
* インデントなしの閉じ丸括弧 `)` でリソースの記述ブロックを閉じます。

```java
import java.io.BufferedReader
import java.io.StringReader

public class SampleTryWithResources
<method>
    public cls void main(String[] args)
        try
        (
            BufferedReader br1 = new BufferedReader(new StringReader("データ1"))
            BufferedReader br2 = new BufferedReader(new StringReader("データ2"))
        )
            System.out.println("br1 から読込: " + br1.readLine())
            System.out.println("br2 から読込: " + br2.readLine())
        catch Exception e
            System.out.println("例外発生: " + e.getMessage())
```
* 関連コード: [SampleTryWithResources.pyja](../../tests/SampleTryWithResources.pyja)
