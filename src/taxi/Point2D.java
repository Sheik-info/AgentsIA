package taxi;

import java.io.Serializable;

public class Point2D implements Serializable {
    private double x;
    private double y;

    public Point2D(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    @Override
    public String toString() {
        return "(" + String.format("%.1f", x) + ", " + String.format("%.1f", y) + ")";
    }
}
