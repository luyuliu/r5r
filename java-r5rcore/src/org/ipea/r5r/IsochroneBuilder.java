package org.ipea.r5r;

import com.conveyal.r5.OneOriginResult;
import com.conveyal.r5.analyst.*;
import com.conveyal.r5.analyst.cluster.RegionalTask;
import com.conveyal.r5.analyst.scenario.Scenario;
import com.conveyal.r5.api.util.LegMode;
import com.conveyal.r5.api.util.TransitModes;
import com.conveyal.r5.transit.TransportNetwork;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

public class IsochroneBuilder {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(IsochroneBuilder.class);

    ForkJoinPool r5rThreadPool;
    TransportNetwork transportNetwork;
    RoutingProperties routingProperties;

    int nIsochrones;

    String[] fromIds;
    double[] fromLats;
    double[] fromLons;

    int[] cutoffs;
    int zoom;

    EnumSet<LegMode> directModes;
    EnumSet<TransitModes> transitModes;
    EnumSet<LegMode> accessModes;
    EnumSet<LegMode> egressModes;

    String departureDate;
    String departureTime;
    int maxWalkTime;
    int maxTripDuration;

    Grid gridPointSet = null;
    private Grid getGridPointSet(int zoom) {
        if (gridPointSet == null || gridPointSet.zoom != zoom) {
            gridPointSet = new Grid(zoom, this.transportNetwork.getEnvelope());
        }

        return gridPointSet;
    }

    private Grid getGridPointSet() {
        return getGridPointSet(9);
    }

    public IsochroneBuilder(ForkJoinPool threadPool, TransportNetwork transportNetwork, RoutingProperties routingProperties) {
        this.r5rThreadPool = threadPool;
        this.transportNetwork = transportNetwork;
        this.routingProperties = routingProperties;
    }

    public void setOrigins(String[] fromIds, double[] fromLats, double[] fromLons) {
        this.fromIds = fromIds;
        this.fromLats = fromLats;
        this.fromLons = fromLons;

        this.nIsochrones = fromIds.length;
    }

    public void setModes(String directModes, String accessModes, String transitModes, String egressModes) {
        this.directModes = Utils.setLegModes(directModes);
        this.accessModes = Utils.setLegModes(accessModes);
        this.egressModes = Utils.setLegModes(egressModes);
        this.transitModes = Utils.setTransitModes(transitModes);

    }

    public void setDepartureDateTime(String departureDate, String departureTime) {
        this.departureDate = departureDate;
        this.departureTime = departureTime;
    }

    public void setTripDuration(int maxWalkTime, int maxTripDuration) {
        this.maxWalkTime = maxWalkTime;
        this.maxTripDuration = maxTripDuration;
    }

    public void setResolution(int resolution) {
        this.zoom = resolution;
    }

    public void setCutoffs(int[] cutoffs) {
        this.cutoffs = cutoffs;
    }

    public List<LinkedHashMap<String, ArrayList<Object>>> build() throws ExecutionException, InterruptedException {
        int[] requestIndices = new int[nIsochrones];
        for (int i = 0; i < nIsochrones; i++) requestIndices[i] = i;

        List<LinkedHashMap<String, ArrayList<Object>>> resultsList;
        resultsList = r5rThreadPool.submit(() ->
                Arrays.stream(requestIndices).parallel()
                        .mapToObj(index -> {
                            LinkedHashMap<String, ArrayList<Object>> results = null;
                            try {
                                results = buildIsochrone(index);
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                            return results;
                        }).
                        collect(Collectors.toList())).get();

        return resultsList;
    }

    private LinkedHashMap<String, ArrayList<Object>> buildIsochrone(int index) throws ParseException {
        RegionalTask request = buildRequest(index);


        request.destinationPointSets = new PointSet[1];
        request.destinationPointSets[0] = getGridPointSet(zoom);

        request.percentiles = new int[1];
        request.percentiles[0] = 50;

        LOG.info("checking grid point set");
        LOG.info(gridPointSet.toString());

        LOG.info(request.getWebMercatorExtents().toString());

        LOG.info("compute travel times");

        TravelTimeComputer computer = new TravelTimeComputer(request, transportNetwork);

        OneOriginResult travelTimeResults = computer.computeTravelTimes();

        int[] times = travelTimeResults.travelTimes.getValues()[0];
        for (int i = 0; i < times.length; i++) {
            // convert travel times from minutes to seconds
            // this test is necessary because unreachable grid cells have travel time = Integer.MAX_VALUE, and
            // multiplying Integer.MAX_VALUE by 60 causes errors in the isochrone algorithm
            if (times[i] <= maxTripDuration) {
                times[i] = times[i] * 60;
            } else {
                times[i] = Integer.MAX_VALUE;
            }
        }

        // Build return table
        WebMercatorExtents extents = WebMercatorExtents.forPointsets(request.destinationPointSets);
        WebMercatorGridPointSet isoGrid = new WebMercatorGridPointSet(extents);

        RDataFrame isochronesTable = new RDataFrame();
        isochronesTable.addStringColumn("from_id", fromIds[index]);
        isochronesTable.addIntegerColumn("cutoff", 0);
        isochronesTable.addStringColumn("geometry", "");


        for (int cutoff:cutoffs) {
            IsochroneFeature isochroneFeature = new IsochroneFeature(cutoff*60, isoGrid, times);

            isochronesTable.append();
            isochronesTable.set("cutoff", cutoff);
            isochronesTable.set("geometry", isochroneFeature.geometry.toString());
        }

        if (isochronesTable.nRow() > 0) {
            return isochronesTable.getDataFrame();
        } else {
            return null;
        }

    }

    private RegionalTask buildRequest(int index) throws ParseException {
        RegionalTask request = new RegionalTask();

        request.scenario = new Scenario();
        request.scenario.id = "id";
        request.scenarioId = request.scenario.id;

        request.zoneId = transportNetwork.getTimeZone();
        request.fromLat = fromLats[index];
        request.fromLon = fromLons[index];
        request.walkSpeed = (float) routingProperties.walkSpeed;
        request.bikeSpeed = (float) routingProperties.bikeSpeed;
        request.streetTime = maxTripDuration;
        request.maxWalkTime = maxWalkTime;
        request.maxBikeTime = maxTripDuration;
        request.maxCarTime = maxTripDuration;
        request.maxTripDurationMinutes = maxTripDuration;
        request.makeTauiSite = false;
        request.recordTimes = true;
        request.recordAccessibility = false;
        request.maxRides = routingProperties.maxRides;
        request.bikeTrafficStress = routingProperties.maxLevelTrafficStress;

        request.directModes = directModes;
        request.accessModes = accessModes;
        request.egressModes = egressModes;
        request.transitModes = transitModes;

        request.date = LocalDate.parse(departureDate);

        int secondsFromMidnight = Utils.getSecondsFromMidnight(departureTime);

        request.fromTime = secondsFromMidnight;
        request.toTime = secondsFromMidnight + (routingProperties.timeWindowSize * 60);

        request.monteCarloDraws = routingProperties.numberOfMonteCarloDraws;
        return request;
    }



}
