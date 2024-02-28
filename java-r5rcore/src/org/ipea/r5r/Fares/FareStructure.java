package org.ipea.r5r.Fares;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.List;

import static org.ipea.r5r.JsonUtil.OBJECT_MAPPER;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FareStructure {

    /**
     * Maximum number of transfers that can have a discount. The transfer fare is obtained from the farePerTransfer
     * Map up to the number of transfers informed in this field. After that, each new leg gets the full fare from the
     * farePerMode Map.
     */
    private int maxDiscountedTransfers;

    /**
     * Maximum time allowed for discounted transfers, counting from the time of  boarding first transit leg
    */
    private int transferTimeAllowance;

    /**
     * Maximum charged fare for any combination of trips.
     */
    private float fareCap;

    @JsonIgnore
    private int integerFareCap;

    private final List<FarePerType> faresPerType;
    private final List<FarePerTransfer> faresPerTransfer;
    private final List <FarePerRoute> faresPerRoute;

    // Getters and Setters

    public int getMaxDiscountedTransfers() {
        return maxDiscountedTransfers;
    }

    public void setMaxDiscountedTransfers(int maxDiscountedTransfers) {
        this.maxDiscountedTransfers = maxDiscountedTransfers;
    }

    public int getTransferTimeAllowance() {
        return transferTimeAllowance;
    }

    @JsonIgnore
    public int getTransferTimeAllowanceSeconds() {
        return transferTimeAllowance * 60;
    }

    public void setTransferTimeAllowance(int transferTimeAllowance) {
        this.transferTimeAllowance = transferTimeAllowance;
    }

    public float getFareCap() {
        return fareCap;
    }

    public int getIntegerFareCap() {
        return integerFareCap;
    }

    public void setFareCap(float fareCap) {
        this.fareCap = fareCap;
        this.integerFareCap = Math.round(fareCap * 100.0f);
    }

    public List<FarePerType> getFaresPerType() {
        return faresPerType;
    }
    public List<FarePerTransfer> getFaresPerTransfer() {
        return faresPerTransfer;
    }
    public List<FarePerRoute> getFaresPerRoute() {
        return faresPerRoute;
    }

    public FareStructure() {
        this.maxDiscountedTransfers = 1;
        this.transferTimeAllowance = 120;
        this.fareCap = -1;

        this.faresPerType = new ArrayList<>();
        this.faresPerTransfer = new ArrayList<>();
        this.faresPerRoute = new ArrayList<>();
    }

    public static FareStructure fromJson(String data) {
        try {
            return OBJECT_MAPPER.readValue(data, FareStructure.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return "";
    }

}
