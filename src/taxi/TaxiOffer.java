package taxi;

import java.io.Serializable;

public class TaxiOffer implements Serializable {
    private double hourlyPrice;
    private double estimatedDuration;
    private double estimatedDistance;

    public TaxiOffer(double price, double estimatedDuration, double estimatedDistance) {
        this.hourlyPrice = price;
        this.estimatedDuration = estimatedDuration;
        this.estimatedDistance = estimatedDistance;
    }

    public double getHourlyPrice() {
        return hourlyPrice;
    }

    public double getEstimatedDuration() {
        return estimatedDuration;
    }

    public double getEstimatedDistance() {
        return estimatedDistance;
    }
}