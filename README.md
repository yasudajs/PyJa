# PyJa

PyJaは、Javaをベースに、Pythonのようなインデントによる構造表現（オフサイドルール）を取り入れた、新しいプログラミング言語（トランスパイラ）のプロトタイプです。

「Javaの堅牢性と豊富なエコシステムを活かしつつ、Pythonのようにスッキリとしたコードを書きたい」というアイデアから生まれました。

また、初学者が躓きやすい点をわかりやすくすることで、プログラミング学習のハードルを下げることも目的としており、教育用ツールとしての活用も想定しています。プログラミング経験者の方には冗長に思われるかもしれませんが、初学者が迷わないように、コードの意味や役割を明記するような記法を追加しています。

---

## 主な特徴(Javaからの変更点)

1.  **インデントによってブロックを表現します**:
    *   中括弧 `{ }` は不要です。半角スペース4つのインデントの深さでコードの構造（クラス、メソッド、ループ、分岐など）を表現します。
2.  **セミコロン `;` の記述は不要です**:
    *   ステートメントの末尾のセミコロンは省略可能です。トランスパイラが自動的に補完します。
3.  **条件式の括弧 `( )` の記述は不要です**:
    *   `if` や `for`、`while` などの条件式を囲む括弧 `( )` を省略して書くことができます。
4.  **丸括弧内での複数行記述が可能です**:
    *   メソッドの引数リストや条件式など、括弧で囲む部分は、その内部であれば自由に改行して記述することができます。
    *   また、横幅が長くなった文字列結合などの式（例：`+` 演算子での結合）も、式全体を丸括弧 `( )` で囲むことで複数行に分けて記述することが可能です。
5.  **厳密なインデント検証を行います**:
    *   タブ文字の混入や、4の倍数以外のスペースインデント、不正なデデント（戻りインデント）を検知し、コンパイル前にエラーを出力します。
6. **アクセス修飾子を刷新し、その記述を必須とします**:
    *   初学者にとって直感的なアクセス修飾子（`public`, `family`, `home`, `private`）を導入します。
    *   クラス宣言、および `<field>`, `<const>`, `<method>`, `<innercls>` セクション内のすべての定義において、アクセス修飾子の明示を**必須**とします。
7. **アノテーションは単独行での記述が必須です**:
    *   アノテーション（`@Override` など）を記述する場合、同じ行に続けてメソッド宣言などのコードを書くことはできません。必ず**単独行**で記述する必要があります。
8.  **セクションタグによるクラスの構造化が必須です**:
    *   クラス（またはインターフェース、enum）の直下には直接メンバーを書くことはできず、必ず `<field>`, `<const>`, `<method>`, `<innercls>` のセクションタグを記述します。
    *   セクションタグの記述による**インデントの追加は不要**（クラスと同じレベル）です。
    *   各セクションは省略可能ですが、記述する場合は必ず **`<field>` -> `<const>` -> `<method>` -> `<innercls>`** の順序でなければなりません（順序違反や重複はコンパイルエラーになります）。
9. **`cls` / `ins` / `new` キーワードによる明示的な宣言が必須です**:

    *   Javaの `static` などの所属を表すキーワードを廃止し、より直感的なキーワードで記述します。
    *   フィールド、メソッド、コンストラクタに加え、**インナークラス（ネストされたクラス、インターフェース、列挙型）の宣言**においてもこれらの指定が必須となります。
    *   `cls` ：クラス（アウタークラス含む）に属するメンバー/ネスト定義（Javaの `static` に変換）
    *   `ins` ：インスタンス（アウターインスタンス含む）に属するメンバー/ネスト定義（Javaでは修飾子なしに変換）
    *   `new` ：コンストラクタ（インスタンス生成時に呼ばれるメソッド。Javaではクラス名と同名のメソッドに変換）

10. **比較演算子は `<` / `<=` のみに固定化され、数直線に沿った記述が必須です**:

    *   初学者の迷いや大小関係の記述ミスを防ぐため、比較時の不等号を `<` および `<=` のみに制限し、`>` および `>=` を禁止します。
    *   常に `小さい値 < 大きい値` の順（数直線の並び）で記述することを強制します（例：`if 5 < x` は許可されますが、`if x > 5` はトランスパイルエラーになります）。
    *   **例（変数 `x` の範囲判定）**:
        *   **0以上100以下（範囲内）**: `0 <= x && x <= 100`  
            （数直線上で `0` と `100` の間に `x` が挟まれている様子が視覚的に一瞬で理解できます）
        *   **0未満、または100より大きい（範囲外）**: `x < 0 || 100 < x`  
            （数直線の外側へ範囲が広がっている様子が、コードの並び順のまま直感的に把握できます）

11. **インスタンス経由でのクラスメンバー（`cls`）へのアクセスは禁止です**:

    *   オブジェクト指向の原則（クラスとインスタンスの役割の明確な分離）を意識させるため、インスタンス変数（小文字で始まる変数名）を経由したクラスメンバー（`cls`）へのドットアクセス（`.`）を禁止します。
    *   クラスメンバーへのアクセスは、常にクラス名（大文字で始まるクラス名）から直接行う必要があります（例：`Syain.getCount()` は許可されますが、`taro.getCount()` はトランスパイルエラーになります）。

---

## PyJaにおけるアクセス修飾子キーワード一覧

PyJaでは、クラス（およびインターフェース、enum）の宣言行、および `<field>`, `<const>`, `<method>`, `<innercls>` セクション内のすべての定義行において、以下のアクセス修飾子のいずれかを行頭（インデントを除く）に明示することが必須です。

| PyJaキーワード | 意味 | Javaへの変換 |
|---|---|---|
| `public` | どこからでもアクセス可能 | `public` |
| `family` | 「同じ家内（同一パッケージ内）」および「家族（継承関係）」からアクセス可能（Javaの `protected` 相当） | `protected` |
| `home` | 「同じ家内（同一パッケージ内）」からのみアクセス可能 | 削除（Javaのパッケージプライベート） |
| `private` | クラス内からのみアクセス可能、外部クラスからはアクセス不可 | `private` |

※ 上記表の上段ほど公開範囲が広く、下段ほど公開範囲は狭い。
※ アノテーション（`@Override` など）が記述されている行は、この行頭アクセス修飾子必須チェックの対象外となりますが、アノテーションの同一行に他のプログラムコードを記述することはできません（必ずアノテーションは単独行にします）。

## PyJaにおけるセクションタグの導入

クラスが持つ要素をセクションタグとして定義し、宣言を必要とします。これにより、クラスの構成要素が以下のように完全に分離されるため、直感的に役割が理解しやすくなります。

| セクションタグ | Java | 意味 |
|---|---|---|
| `<field>` | field | フィールド（変数） |
| `<const>` | constructor | コンストラクタ（初期化） |
| `<method>` | method | メソッド（動的な処理） |
| `<innercls>` | inner class | ネストされたクラス |

## PyJaにおけるメンバー修飾子キーワード一覧

| PyJaキーワード | 意味 | Javaへの変換 |
|---|---|---|
| `cls` | クラス（またはアウタークラス）に属するメンバー（フィールド・メソッド・初期化ブロック・インナークラス） | `static` |
| `ins` | インスタンス（またはアウターインスタンス）に属するメンバー（フィールド・メソッド・インナークラス） | 削除（Javaでは不要） |
| `new` | コンストラクタ | 削除（クラス名と一致で識別） |

セクションタグは上記の順番で記述することが必要であり、順番を入れ替えたり混ぜて記述したりすることはできません（不要なセクションは省略可能です）。
また、セクションタグの内部に記述するメンバーは、クラス定義（またはセクションタグ）のインデントレベルから、さらに4スペース下げて記述する必要があります。

## 記述例

#### クラスの記述例

```java
public class Counter
<field>
    // クラスフィールド（cls必須）
    private cls int total = 0

    // クラス初期化ブロック
    cls
        total = 0

    // インスタンスフィールド（ins必須）
    private ins int count

<const>
    // コンストラクタ（new必須）
    public new Counter(int start)
        this.count = start
        total++

<method>
    // インスタンスメソッド（ins必須）
    public ins int getCount()
        return count

    // クラスメソッド（cls必須）
    public cls int getTotal()
        return total
```

#### インターフェースの記述例（`default ins` メソッドの記述例）

```java
public interface Drawable
<method>
    // 抽象メソッド（ins必須）
    public ins void draw()

    // デフォルトメソッド（default ins必須）
    public default ins void printStatus()
        System.out.println("Status: OK")
```

#### enum (列挙型) の記述例

シンプルな enum は、`<field>` セクション内にカンマ区切りで定数を並べて記述します。

```java
public enum Direction
<field>
    NORTH, SOUTH, EAST, WEST
```

フィールドやメソッドを持つ enum の場合は、通常のクラスと同様に `<const>` や `<method>` セクションを使用して記述します。

```java
public enum Role
<field>
    ADMIN, USER, GUEST
    private ins String label

<const>
    private new Role(String label)
        this.label = label

<method>
    public ins String getLabel()
        return label
```

#### ネストクラス（内部クラス）の記述例

```java
public class Outer
<field>
    private ins String name

<const>
    public new Outer()
        this.name = "Default"

<method>
    public ins void print()
        System.out.println(name)

<innercls>
    public cls class InnerHelper
    <field>
        private ins int id
    <method>
        public ins void help()
            System.out.println("Inner helper id: " + id)
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

public class Sample
<method>
    public cls void main(String[] args)
        System.out.println("--- PyJa 動作テスト ---")

        // --- 基本的なループ ---
        int limit = 5
        for int i = 0; i < limit; i++
            System.out.println("ループカウンタ: " + i)

        // --- while文 ---
        int w = 0
        while w < limit
            System.out.println("whileカウンタ: " + w)
            w++

        // --- do-while文 ---
        int d = 0
        do
            System.out.println("do-whileカウンタ: " + d)
            d++
        while d < limit

        // --- 条件分岐 ---
        int x = 10
        if 5 < x
            System.out.println("xは5より大きいです")
            if x == 10
                System.out.println("xはちょうど10です")
        else
            System.out.println("xは5以下です")

        // --- switch文・switch式の使用例 ---
        int score = 2
        switch score
            case 1
                System.out.println("Score: 1")
                break
            case 2
                System.out.println("Score: 2")
                break
            default
                System.out.println("Score: Other")

        String text = switch score
            case 1 -> "One"
            case 2 -> "Two"
            default -> "Other"
        System.out.println("Text: " + text)

        // --- リストの使用例 ---
        List<String> languages = new ArrayList<>()
        languages.add("Java")
        languages.add("Python")
        languages.add("PyJa")

        for String lang : languages
            System.out.println("言語: " + lang)

        // --- 複数行にわたる文字列結合（括弧で囲む） ---
        String nonTextBlockStr = ("You\'re"
            + "\n" + "nice" + "\n" + "Engineer!")
        System.out.println(nonTextBlockStr)

        // --- 複数行にわたるメソッド呼び出し ---
        printMessage(
            "Hello, " +
            "World from PyJa!"
        )

    public cls void printMessage(String msg)
        System.out.println("メッセージ: " + msg)
```

---

## サンプルコード2: オブジェクト指向と複数クラス (`Syain.pyja`, `Meibo.pyja`)

PyJaのオブジェクト指向表現（`cls` クラス変数/メソッド、`ins` インスタンス変数/メソッド、`new` コンストラクタ）と、複数ファイルが連携する事例です。

このサンプルでは、アクセス制限を利用したカプセル化を表現しています。
*   `Syain` はパッケージ内でのみ利用する内部クラスとするため `home class` としています。
*   `Meibo` は外部から実行されるエントリポイントとなるため `public class` としています。
これにより、パッケージ外からは `Syain` を直接利用できず、`Meibo` を介して間接的に利用する安全な設計になります。

### Syain.pyja
```java
home class Syain
<field>
    // クラス変数（登録された総社員数）
    private cls int count = 0

    // インスタンス変数
    private ins int id
    private ins String name
    private ins int age

<const>
    public new Syain(int id, String name, int age)
        this.id = id
        this.name = name
        this.age = age
        count++ // インスタンス生成時にカウントアップ

<method>
    // インスタンスメソッド
    public ins void printInfo()
        System.out.println("社員番号: " + id + ", 名前: " + name + ", 年齢: " + age)

    // クラスメソッド
    public cls int getCount()
        return count
```

### Meibo.pyja
```java
import java.util.ArrayList
import java.util.List

public class Meibo
<method>
    public cls void main(String[] args)
        List<Syain> list = new ArrayList<>()
        list.add(new Syain(1, "山田太郎", 30))
        list.add(new Syain(2, "佐藤花子", 25))
        list.add(new Syain(3, "鈴木一郎", 40))

        System.out.println("--- 社員名簿一覧 ---")
        for (Syain s : list)
            s.printInfo()

        System.out.println()
        System.out.println("登録社員数: " + Syain.getCount() + "名")
```

---

## 使い方

### Windows の場合

#### 1. コンパイル
`pyjac.bat` を使用して `.pyja` ファイルをコンパイルします。内部で `.java` ファイルの生成と、`javac` によるコンパイルが自動で行われます。

```cmd
.\pyjac.bat Sample.pyja
```

複数ファイルからなるプログラム（例：`Syain`/`Meibo`）をコンパイルする場合は、依存される（呼び出される）クラスから順に個別にコンパイルします。
```cmd
.\pyjac.bat Syain.pyja
.\pyjac.bat Meibo.pyja
```

#### 2. プログラムの実行
`pyja.bat` を使用して、生成されたクラスファイルを実行します。

```cmd
.\pyja.bat Sample
```

複数ファイルの場合は、`main` メソッドがある実行クラスを指定して実行します。
```cmd
.\pyja.bat Meibo
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

複数ファイルの場合は順に個別にコンパイルします。
```bash
./pyjac Syain.pyja
./pyjac Meibo.pyja
```

#### 2. プログラムの実行
`pyja` を使用して実行します。

```bash
./pyja Sample
```

複数ファイルの場合は、`main` メソッドがある実行クラスを指定して実行します。
```bash
./pyja Meibo
```

---

## ライセンス

このプロジェクトは **MIT License** のもとで公開されています。利用・改変・再配布にあたっては、著作権表示およびライセンス表示を含める必要があります。

詳細は [LICENSE](LICENSE) ファイルをご覧ください。

Copyright (c) 2026 安田情報システム
