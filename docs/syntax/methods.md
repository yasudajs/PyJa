# メソッド

PyJaでは、クラスメソッドやインスタンスメソッドを定義する際、アクセス修飾子およびメンバー修飾子の明示を必須とし、コードの可読性を高めています。

---

## 1. メソッドの定義ルール

### 1.1. 修飾子の明示が必須
すべてのメソッド定義において、以下の修飾子を記述することが必須です。
1. **アクセス修飾子**（`public`, `family`, `home`, `private` のいずれか）
2. **メンバー修飾子**（`cls` (クラス所属) または `ins` (インスタンス所属) のいずれか）

#### 記述順序
`[アクセス修飾子] [メンバー修飾子] [戻り値型] [メソッド名]([引数リスト])`

* **クラスメソッドの例 (Javaの `static` に変換)**:
  ```java
  public cls int add(int a, int b)
      return a + b
  ```
* **インスタンスメソッドの例 (Javaでは修飾子なしに変換)**:
  ```java
  private ins void printResult(int value)
      System.out.println("Result: " + value)
  ```

---

## 2. メソッド呼び出しと複数行記述

### 2.1. メソッド呼び出し時の丸括弧内での改行
引数が多くて横に長くなる場合、メソッド呼び出しの丸括弧 `( )` 内であれば自由に改行して記述できます。

```java
printMessage(
    "Hello, " +
    "World from PyJa!"
)
```

---

## 3. アノテーションの単独行記述

メソッドに `@Override` などのアノテーションを付与する場合、アノテーションは必ず **単独行** で記述しなければなりません。同じ行に続けてメソッド宣言を書くことはできません。

```java
@Override
public ins String toString()
    return "Syain{id=" + id + "}"
```

* 関連コード: [Sample.pyja](../../samples/Sample.pyja), [Syain.pyja](../../samples/Syain.pyja)
