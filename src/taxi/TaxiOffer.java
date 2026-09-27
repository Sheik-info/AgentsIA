package taxi;

import java.io.Serializable;

public class TaxiOffer implements Serializable {
    private double price;
    private double estimatedDuration;
    private double estimatedDistance;

    public TaxiOffer(double price, double estimatedDuration, double estimatedDistance) {
        this.price = price;
        this.estimatedDuration = estimatedDuration;
        this.estimatedDistance = estimatedDistance;
    }

    public double getPrice() {
        return price;
    }

    public double getEstimatedDuration() {
        return estimatedDuration;
    }

    public double getEstimatedDistance() {
        return estimatedDistance;
    }
}