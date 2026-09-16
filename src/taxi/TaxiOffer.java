package taxi;

import java.io.Serializable;

public class TaxiOffer implements Serializable {
    private int price;
    private int eta;

    public TaxiOffer(int price, int eta) {
        this.price = price;
        this.eta = eta;
    }

    public int getPrice() {
        return price;
    }

    public int getEta() {
        return eta;
    }
}