package taxi;

import java.io.Serializable;

public class ClientRequest implements Serializable {
    private Point2D departurePoint;
    private Point2D destinationPoint;
    private double maxDistanceIncrease;

    public ClientRequest(Point2D departurePoint, Point2D destinationPoint, double maxDistanceIncrease) {
        this.departurePoint = departurePoint;
        this.destinationPoint = destinationPoint;
        this.maxDistanceIncrease = maxDistanceIncrease;
    }

    public Point2D getDeparturePoint() {
        return departurePoint;
    }

    public Point2D getDestinationPoint() {
        return destinationPoint;
    }

    public double getMaxDistanceIncrease() {
        return maxDistanceIncrease;
    }
}