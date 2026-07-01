class Syain {

    // クラス変数（登録された総社員数）
    private static int count = 0;

    // インスタンス変数
    private int id;
    private String name;
    private int age;


    public Syain(int id, String name, int age) {
        this.id = id;
        this.name = name;
        this.age = age;
        count++; // インスタンス生成時にカウントアップ

    }

    // インスタンスメソッド
    public void printInfo() {
        System.out.println("社員番号: " + id + ", 名前: " + name + ", 年齢: " + age);

    // クラスメソッド
    }
    public static int getCount() {
        return count;
    }
}
