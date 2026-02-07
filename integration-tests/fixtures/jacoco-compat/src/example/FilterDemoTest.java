package example;

public class FilterDemoTest {
    public static void main(String[] args) {
        FilterDemo demo = new FilterDemo();
        // Call filtered methods
        demo.equals(new Object());
        demo.hashCode();
        demo.toString();
        // Call kept methods  
        int val = demo.computeValue(5);
        assert val == 11 : "computeValue(5) should be 11";
        boolean pos = demo.isPositive(42);
        assert pos : "42 should be positive";
        System.out.println("All tests passed!");
    }
}
