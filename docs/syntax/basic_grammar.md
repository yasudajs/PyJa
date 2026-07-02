# 基本構文

PyJaでは、Javaの冗長なコードをスッキリさせるため、インデントによるブロック表現（オフサイドルール）や記述制限などのルールを導入しています。

---

## 1. コードの表記ルール

### 1.1. インデントによるブロック表現（中括弧 `{ }` の廃止）
Javaにおける `{ }` は不要です。**半角スペース4つ**のインデントの深さで、クラス、メソッド、ループ、分岐などの構造を表現します。
タブ文字の混入や、4の倍数以外のスペースインデント、不正なインデント戻りはコンパイルエラーとなります。

### 1.2. セミコロン `;` の省略
ステートメント（文）の末尾のセミコロンは不要です。改行を検知してコンパイラが自動的に補完します。

### 1.3. 括弧内および文字列結合の複数行記述
メソッドの引数リストや条件式など、丸括弧 `( )` で囲む部分は、その内部であれば自由に改行して記述できます。
また、長い文字列結合などの式も、式全体を `( )` で囲むことで、複数行に分けて記述することが可能です。

```java
String nonTextBlockStr = ("You\'re"
    + "\n" + "nice" 
    + "\n" + "Engineer!")
```

---

## 2. 条件分岐とループ

### 2.1. 条件分岐 (if)
条件式を囲む丸括弧 `( )` は不要です。

```java
int x = 10
if 5 < x
    System.out.println("xは5より大きいです")
else
    System.out.println("xは5以下です")
```

#### 比較演算子の制限
初学者の混乱や不等号の記述ミスを防ぐため、比較時の不等号は **`<` および `<=` のみに制限** されています。`>` や `>=` を記述するとコンパイルエラーになります。
常に `小さい値 < 大きい値` の順（数直線の並び）で記述する必要があります。

* **例（変数 `x` の範囲判定）**:
  * 0以上100以下（範囲内）: `0 <= x && x <= 100`  
  * 0未満、または100より大きい（範囲外）: `x < 0 || 100 < x`  

* 関連コード: [IfTest.pyja](../../tests/IfTest.pyja)

### 2.2. 多岐分岐 (switch)
Javaと同様に `switch` 文および値を返す `switch` 式をサポートしています。

```java
// switch文
switch score
    case 1
        System.out.println("Score: 1")
        break
    case 2
        System.out.println("Score: 2")
        break
    default
        System.out.println("Score: Other")

// switch式
String text = switch score
    case 1 -> "One"
    case 2 -> "Two"
    default -> "Other"
```
* 関連コード: [SwitchTest.pyja](../../tests/SwitchTest.pyja)

### 2.3. ループ処理 (for / while)
条件式や初期化式を囲む丸括弧 `( )` は不要です。

```java
// for文（初期化式の前にデータ型を記述）
for int i = 0; i < 5; i++
    System.out.println("i: " + i)

// 拡張for文
for String lang : languages
    System.out.println("言語: " + lang)

// while文
int w = 0
while w < 5
    System.out.println("w: " + w)
    w++

// do-while文
int d = 0
do
    System.out.println("d: " + d)
    d++
while d < 5
```
* 関連コード: [WhileTest.pyja](../../tests/WhileTest.pyja)

### 2.4. break / continue とラベルの利用
Javaと同様にループ制御キーワードが利用できます。多重ループから一気に抜けるためのラベル指定も中括弧なしで記述可能です。

```java
outerLoop:
for int i = 0; i < 3; i++
    for int j = 0; j < 3; j++
        if i == 1 && j == 1
            break outerLoop
```
* 関連コード: [LabelTest.pyja](../../tests/LabelTest.pyja)
