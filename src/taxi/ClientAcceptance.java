package taxi;

import java.io.Serializable;

public class ClientAcceptance implements Serializable {
    private Point2D departurePoint;
    private Point2D destinationPoint;

    public ClientAcceptance(Point2D departurePoint, Point2D destinationPoint) {
        this.departurePoint = departurePoint;
        this.destinationPoint = destinationPoint;
    }

    public Point2D getDeparturePoint() {
        return departurePoint;
    }

    public Point2D getDestinationPoint() {
        return destinationPoint;
    }
}