package taxi;

import java.io.Serializable;

public class ClientAcceptance implements Serializable {
    private Point2D departurePoint;
    private Point2D destinationPoint;
    private double estimatedDuration;

    public ClientAcceptance(Point2D departurePoint, Point2D destinationPoint, double estimatedDistance) {
        this.departurePoint = departurePoint;
        this.destinationPoint = destinationPoint;
        this.estimatedDuration = estimatedDistance;
    }

    public Point2D getDeparturePoint() {
        return departurePoint;
    }

    public Point2D getDestinationPoint() {
        return destinationPoint;
    }

    public double getEstimatedDuration() {
        return estimatedDuration;
    }
}