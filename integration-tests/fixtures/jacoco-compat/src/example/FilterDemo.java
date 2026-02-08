package example;

public class FilterDemo {
    // This method should be filtered by rules (e.g., matched by equals rule)
    public boolean equals(Object other) {
        return this == other;
    }
    
    // This method should be filtered by rules (e.g., matched by hashCode rule)
    public int hashCode() {
        return System.identityHashCode(this);
    }
    
    // This method should be filtered by rules (e.g., matched by toString rule)
    public String toString() {
        return "FilterDemo";
    }
    
    // This method should REMAIN in coverage (real business logic)
    public int computeValue(int input) {
        return input * 2 + 1;
    }
    
    // Another method that should REMAIN
    public boolean isPositive(int number) {
        return number > 0;
    }
}
