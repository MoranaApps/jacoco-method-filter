package example;

public class DataPoint {

    private String identifier;
    private double xCoordinate;
    private double yCoordinate;

    public DataPoint(String identifier, double xCoordinate, double yCoordinate) {
        this.identifier = identifier;
        this.xCoordinate = xCoordinate;
        this.yCoordinate = yCoordinate;
    }

    public double computeDistanceFromOrigin() {
        return Math.sqrt(xCoordinate * xCoordinate + yCoordinate * yCoordinate);
    }

    public boolean isInQuadrant(int quadrantNumber) {
        if (quadrantNumber == 1) {
            return xCoordinate > 0 && yCoordinate > 0;
        } else if (quadrantNumber == 2) {
            return xCoordinate < 0 && yCoordinate > 0;
        } else if (quadrantNumber == 3) {
            return xCoordinate < 0 && yCoordinate < 0;
        } else if (quadrantNumber == 4) {
            return xCoordinate > 0 && yCoordinate < 0;
        }
        return false;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public double getXCoordinate() {
        return xCoordinate;
    }

    public void setXCoordinate(double xCoordinate) {
        this.xCoordinate = xCoordinate;
    }

    public double getYCoordinate() {
        return yCoordinate;
    }

    public void setYCoordinate(double yCoordinate) {
        this.yCoordinate = yCoordinate;
    }

    public boolean equals(Object other) {
        if (other == this) return true;
        if (!(other instanceof DataPoint)) return false;
        DataPoint otherPoint = (DataPoint) other;
        boolean idMatches = (identifier == null && otherPoint.identifier == null) ||
                           (identifier != null && identifier.equals(otherPoint.identifier));
        boolean xMatches = Double.compare(xCoordinate, otherPoint.xCoordinate) == 0;
        boolean yMatches = Double.compare(yCoordinate, otherPoint.yCoordinate) == 0;
        return idMatches && xMatches && yMatches;
    }

    public int hashCode() {
        int result = 1;
        result = 37 * result + (identifier == null ? 0 : identifier.hashCode());
        long xBits = Double.doubleToLongBits(xCoordinate);
        result = 37 * result + (int)(xBits ^ (xBits >>> 32));
        long yBits = Double.doubleToLongBits(yCoordinate);
        result = 37 * result + (int)(yBits ^ (yBits >>> 32));
        return result;
    }

    public String toString() {
        return "DataPoint(" + identifier + "," + xCoordinate + "," + yCoordinate + ")";
    }
}
