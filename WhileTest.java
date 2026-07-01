public class WhileTest {

    public static void main(String[] args) {
        // while文のテスト
        int i = 0;
        while (i < 5) {
            System.out.println("while count: " + i);
            i++;

        }
        System.out.println();

        // do-while文のテスト
        int j = 0;
        do {
            System.out.println("do-while count: " + j);
            j++;
        }
        while (j < 5);
    }
}
