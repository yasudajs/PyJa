# 基本構造

PyJaでは、Javaのクラスやその他の構造（インターフェース、列挙型、内部クラス）をスッキリ記述するため、中括弧 `{ }` を廃止し、インデント（スペース4つ）による構造表現を採用しています。
また、クラスのメンバーを明確に整理するための **セクションタグ** の導入が特徴です。

---

## 1. セクションタグによるクラスの構造化

クラス（またはインターフェース、enum）の直下には直接メンバーを記述することはできません。メンバーはその役割に応じて、必ず以下のセクションタグの中に記述する必要があります。

| セクションタグ | 役割 | 対応するJavaの概念 |
| :--- | :--- | :--- |
| `<field>` | クラス・インスタンス変数の定義 | フィールド（メンバー変数） |
| `<const>` | コンストラクタ（初期化）の定義 | コンストラクタ |
| `<method>` | メソッドの定義 | メソッド |
| `<innercls>` | ネストされたクラス等の定義 | インナークラス、内部インターフェースなど |

### 記述ルール
* セクションタグを記述する際、**インデントを追加する必要はありません**（クラス宣言と同じインデントレベルになります）。
* セクションタグの内部に記述するメンバーは、タグからさらに **4スペース下げて** 記述します。
* 使用しないセクションは省略可能ですが、記述する場合は必ず **`<field>` ➡ `<const>` ➡ `<method>` ➡ `<innercls>`** の順序で記述する必要があります。順序が異なっていたり、同じタグが重複して出現した場合はコンパイルエラーになります。

---

## 2. 各構造の定義方法

### 2.1. クラス (Class)
クラス宣言には、アクセス修飾子（`public` など）が必須です。クラス内のメンバーは、ルールに従い最大4つのセクションタグ（`<field>`、`<const>`、`<method>`、`<innercls>`）に分類して記述します。

```java
public class Hello
<field>
    private ins String message

<const>
    public new Hello(String message)
        this.message = message

<method>
    public ins void greet()
        System.out.println(message)
        
<innercls>
    public cls class Inner
    <method>
        public ins void hello()
            System.out.println("Hello from Inner!")
```

### 2.2. インターフェース (Interface)
インターフェースも同様にセクションタグを使用して構造化します。

```java
public interface Printable
<method>
    public ins void print()
```

### 2.3. 列挙型 (Enum)
列挙型（enum）は、不変の定数を管理するためのシンプルな構造です。
そのため、クラスのようなセクションタグ（`<field>` 等）や修飾子（`public cls` 等）の記述は不要で、直下に列挙子を直接記述します。

```java
public enum Role
    ADMIN, USER, GUEST
```

> [!NOTE]
> 現在のPyJaコンパイラは、独自のメソッドやフィールドを持つ複雑な `enum` 定義には対応しておらず、定数の列挙のみをサポートしています。

### 2.4. 内部クラス (Inner Class)
クラスの内部に別のクラスを定義する場合、親クラスの `<innercls>` セクションタグ内に記述します。

```java
public class Outer
<innercls>
    public cls class Inner
    <method>
        public ins void hello()
            System.out.println("Hello from Inner!")
```
* `cls`（静的インナークラス：Javaの `static class`）または `ins`（非静的インナークラス：Javaの `class`）の指定が必須となります。
