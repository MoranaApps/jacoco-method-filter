package example;

import org.junit.jupiter.api.Test;

class DataPointTest {

    @Test
    void distanceCalculationFromOrigin() {
        DataPoint pt = new DataPoint("P1", 3.0, 4.0);
        double dist = pt.computeDistanceFromOrigin();
        
        if (Math.abs(dist - 5.0) > 0.001) {
            throw new AssertionError("Distance should be 5.0");
        }
    }

    @Test
    void quadrantDetectionWorks() {
        DataPoint q1 = new DataPoint("Q1", 1.0, 1.0);
        DataPoint q2 = new DataPoint("Q2", -1.0, 1.0);
        DataPoint q3 = new DataPoint("Q3", -1.0, -1.0);
        DataPoint q4 = new DataPoint("Q4", 1.0, -1.0);
        
        if (!q1.isInQuadrant(1)) throw new AssertionError("Q1 should be in quadrant 1");
        if (!q2.isInQuadrant(2)) throw new AssertionError("Q2 should be in quadrant 2");
        if (!q3.isInQuadrant(3)) throw new AssertionError("Q3 should be in quadrant 3");
        if (!q4.isInQuadrant(4)) throw new AssertionError("Q4 should be in quadrant 4");
    }

    @Test
    void gettersProvideValues() {
        DataPoint pt = new DataPoint("TEST", 7.5, 8.5);
        
        if (!"TEST".equals(pt.getIdentifier())) throw new AssertionError();
        if (Math.abs(pt.getXCoordinate() - 7.5) > 0.001) throw new AssertionError();
        if (Math.abs(pt.getYCoordinate() - 8.5) > 0.001) throw new AssertionError();
    }

    @Test
    void settersModifyValues() {
        DataPoint pt = new DataPoint("OLD", 0.0, 0.0);
        pt.setIdentifier("NEW");
        pt.setXCoordinate(10.0);
        pt.setYCoordinate(20.0);
        
        if (!"NEW".equals(pt.getIdentifier())) throw new AssertionError();
        if (Math.abs(pt.getXCoordinate() - 10.0) > 0.001) throw new AssertionError();
        if (Math.abs(pt.getYCoordinate() - 20.0) > 0.001) throw new AssertionError();
    }

    @Test
    void equalPointsMatch() {
        DataPoint p1 = new DataPoint("A", 5.0, 6.0);
        DataPoint p2 = new DataPoint("A", 5.0, 6.0);
        
        if (!p1.equals(p2)) throw new AssertionError("Equal points should match");
        if (p1.hashCode() != p2.hashCode()) throw new AssertionError("Hash codes should match");
    }

    @Test
    void toStringContainsData() {
        DataPoint pt = new DataPoint("XYZ", 1.5, 2.5);
        String repr = pt.toString();
        
        if (!repr.contains("XYZ")) throw new AssertionError("Should contain identifier");
        if (!repr.contains("1.5")) throw new AssertionError("Should contain x coordinate");
    }
}
