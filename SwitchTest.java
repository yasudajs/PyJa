public class SwitchTest {

    public static void main(String[] args) {
        System.out.println("--- SwitchTest ---");
        testSwitchStatement(1);
        testSwitchStatement(2);
        testSwitchExpression(1);
        testSwitchExpression(2);

    }
    public static void testSwitchStatement(int val) {
        switch (val) {
            case 1: {
                System.out.println("Statement: One");
                break;
            }
            case 2: {
                System.out.println("Statement: Two");
                break;
            }
            default: {
                System.out.println("Statement: Other");

            }
        }
    }
    public static int testSwitchExpression(int val) {
        int result = switch (val) {
            case 1 -> 10;
            case 2 -> 20;
            default -> 30;
        };
        System.out.println("Expression: " + result);
        return result;
    }
}
