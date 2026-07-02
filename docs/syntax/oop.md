# オブジェクト指向

PyJaでは、オブジェクト指向プログラミング（OOP）の基本概念であるカプセル化や、クラスとインスタンスの役割分担を意識しやすいキーワード設計やアクセス制限を導入しています。

---

## 1. メンバー修飾子 (`cls` / `ins` / `new`)

すべての定義（フィールド、メソッド、インナークラスなど）において、それらが「クラスに属するのか」「インスタンスに属するのか」あるいは「コンストラクタか」を明示する必要があります。

| キーワード | 役割 | 対応するJavaの概念 |
| :--- | :--- | :--- |
| `cls` | クラス（静的）に属する定義 | `static` 修飾子に変換されます。 |
| `ins` | インスタンスに属する定義 | Javaでは修飾子なしに変換されます。 |
| `new` | コンストラクタ（クラスの初期化）の定義 | クラス名と同名のメソッド（コンストラクタ）に変換されます。 |

### 記述例
```java
public class Syain
<field>
    private cls int count = 0 // クラス変数（static変数）
    private ins int id        // インスタンス変数

<const>
    public new Syain(int id)   // コンストラクタ
        this.id = id
        count++

<method>
    public ins void printId()  // インスタンスメソッド
        System.out.println("ID: " + id)

    public cls int getCount()  // クラスメソッド（staticメソッド）
        return count
```
* 関連コード: [Syain.pyja](../../samples/Syain.pyja), [Meibo.pyja](../../samples/Meibo.pyja)

---

## 2. アクセス修飾子

PyJaでは、メンバー（変数、メソッド）およびクラス宣言のアクセス修飾子を刷新し、その記述を **必須** としています。

| 修飾子 | 公開範囲 | Javaへの変換 |
| :--- | :--- | :--- |
| `public` | すべてのクラスからアクセス可能 | `public` |
| `family` | 同一パッケージ内、およびサブクラス（継承先）からアクセス可能 | `protected` |
| `home` | 同一パッケージ内からのみアクセス可能 | Javaのパッケージプライベート（修飾子なし）に変換されます |
| `private` | 同一クラス内からのみアクセス可能 | `private` |

---

## 3. クラスメンバーへのアクセス制限（カプセル化）

クラスメンバー（`cls` 変数や `cls` メソッド）へのアクセスは、**常にクラス名から直接行う必要**があります。
インスタンス変数（小文字で始まる変数名）を経由したクラスメンバーへのアクセスは、オブジェクト指向の原則に基づき、コンパイルエラーとして禁止されます。

```java
// クラス定義
public class Syain
<method>
    public cls int getCount()
        return 1

// 呼び出し側のコード
Syain taro = new Syain()

// ⭕ 許可される（クラス名から直接アクセス）
int c1 = Syain.getCount()

// ❌ コンパイルエラー（インスタンス変数経由でのアクセス）
int c2 = taro.getCount()
```
