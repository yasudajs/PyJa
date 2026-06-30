# PyJa

PyJaは、Javaをベースに、Pythonのようなインデントによる構造表現（オフサイドルール）を取り入れた、新しいプログラミング言語（トランスパイラ）のプロトタイプです。

「Javaの堅牢性と豊富なエコシステムを活かしつつ、Pythonのようにスッキリとしたコードを書きたい」というアイデアから生まれました。

---

## 主な特徴

1.  **インデントによるブロック表現**:
    *   中括弧 `{ }` は不要です。半角スペース4つのインデントの深さでコードの構造（クラス、メソッド、ループ、分岐など）を表現します。
2.  **セミコロン `;` の自動補完**:
    *   ステートメントの末尾のセミコロンは省略可能です。トランスパイラが自動的に補完します。
3.  **条件式の括弧 `( )` の省略**:
    *   `if` や `for`、`while` などの条件式を囲む括弧 `( )` を省略して書くことができます。
4.  **丸括弧内での複数行記述**:
    *   メソッドの引数リストなど、括弧の内部であれば自由に改行して複数行にわたるコードを書くことができます。
5.  **厳密なインデント検証**:
    *   タブ文字の混入や、4の倍数以外のスペースインデント、不正なデデント（戻りインデント）を検知し、コンパイル前にエラーを出力します。
6.  **`cls` / `ins` / `new` キーワードによる明示的なメンバー宣言**:
    *   Javaの `static` を廃止し、より直感的なキーワードで記述します。
    *   `cls` ：クラスに属するメンバー（Javaの `static` に変換）
    *   `ins` ：インスタンスに属するメンバー（Javaでは修飾子なしに変換）
    *   `new` ：コンストラクタ（インスタンス生成時に呼ばれるメソッド）

---

## 修飾子キーワード一覧

| PyJaキーワード | 意味 | Javaへの変換 |
|---|---|---|
| `cls` | クラスに属するメンバー（フィールド・メソッド・初期化ブロック） | `static` |
| `ins` | インスタンスに属するメンバー（フィールド・メソッド） | 削除（Javaでは不要） |
| `new` | コンストラクタ | 削除（クラス名と一致で識別） |

### 記述例

```java
class Counter
    // クラスフィールド（cls必須）
    private cls int total = 0

    // クラス初期化ブロック
    cls
        total = 0

    // インスタンスフィールド（ins必須）
    private ins int count

    // コンストラクタ（new必須）
    public new Counter(int start)
        this.count = start
        total++

    // インスタンスメソッド（ins必須）
    public ins int getCount()
        return count

    // クラスメソッド（cls必須）
    public cls int getTotal()
        return total
```

---

## 動作環境

*   **OS**: Windows, macOS, Linux (Javaが動作するすべての環境)
*   **Java**: JDK (Java Development Kit) 11 以上

---

## セットアップ

### 1. リポジトリのクローン

```bash
git clone https://github.com/yasudajs/PyJa.git
cd PyJa
```

### 2. IDE拡張機能のインストール（任意）

VS Code系のIDEをお使いの場合、PyJa専用のシンタックスハイライト（色付け）とファイルアイコンを有効にするための拡張機能をインストールできます。

対応IDE：VS Code、Antigravity IDE、Cursor、Windsurf、VS Codium

**Windows:**
```cmd
.\install-extension.bat
```

**macOS / Linux:**
```bash
chmod +x install-extension.sh
./install-extension.sh
```

インストール後、**IDEを再起動**すると `.pyja` ファイルに色付けとアイコンが適用されます。

> **アンインストール**: 拡張機能を削除する場合は `uninstall-extension.bat`（Windows）または `./uninstall-extension.sh`（macOS/Linux）を実行してください。

---

## サンプルコード (`Sample.pyja`)

```java
import java.util.ArrayList
import java.util.List

class Sample
    public cls void main(String[] args)
        System.out.println("--- PyJa 動作テスト ---")
        
        int limit = 5
        for int i = 0; i < limit; i++
            System.out.println("ループカウンタ: " + i)
            
        int x = 10
        if x > 5
            System.out.println("xは5より大きいです")
            if x == 10
                System.out.println("xはちょうど10です")
        else
            System.out.println("xは5以下です")
            
        // 複数行にわたるメソッド呼び出し
        printMessage(
            "Hello, " +
            "World from PyJa!"
        )

    public cls void printMessage(String msg)
        System.out.println("メッセージ: " + msg)
```

---

## 使い方

### Windows の場合

#### 1. コンパイル
`pyjac.bat` を使用して `.pyja` ファイルをコンパイルします。内部で `.java` ファイルの生成と、`javac` によるコンパイルが自動で行われます。

```cmd
.\pyjac.bat Sample.pyja
```

#### 2. プログラムの実行
`pyja.bat` を使用して、生成されたクラスファイルを実行します。

```cmd
.\pyja.bat Sample
```

---

### macOS / Linux の場合

#### 0. 実行権限の付与 (初回のみ)
```bash
chmod +x pyjac pyja
```

#### 1. コンパイル
`pyjac` を使用して `.pyja` ファイルをコンパイルします。

```bash
./pyjac Sample.pyja
```

#### 2. プログラムの実行
`pyja` を使用して実行します。

```bash
./pyja Sample
```

---

## ライセンス

このプロジェクトは **MIT License** のもとで公開されています。利用・改変・再配布にあたっては、著作権表示およびライセンス表示を含める必要があります。

詳細は [LICENSE](LICENSE) ファイルをご覧ください。

Copyright (c) 2026 安田情報システム
