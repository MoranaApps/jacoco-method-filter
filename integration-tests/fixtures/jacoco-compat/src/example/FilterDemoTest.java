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

        // --- Flags & predicates coverage ---
        StringContainer sc = new StringContainer("hello");

        // Triggers the covariant get() AND the synthetic bridge Object get()
        String directGet = sc.get();
        assert "hello".equals(directGet) : "get() should return 'hello'";

        // Call via base type to exercise the bridge method at runtime
        BaseContainer<String> base = sc;
        Object bridgeGet = base.get();
        assert "hello".equals(bridgeGet) : "bridge get() should return 'hello'";

        // initData — void-returning, matched by ret:V + name-starts:init
        sc.initData();

        // processData — matched by name-contains:Data (excluded)
        String processed = sc.processData();
        assert "HELLO".equals(processed) : "processData should return 'HELLO'";

        // getData — matched by name-contains:Data but RESCUED by include rule
        String data = sc.getData();
        assert "hello".equals(data) : "getData should return 'hello'";

        System.out.println("All tests passed!");
    }
}
