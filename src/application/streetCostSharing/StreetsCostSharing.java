package application.streetCostSharing;
import application.dynamic_routing.VotingStreetCostData;
import model.GeoCar;
import model.OSMgraph.Way;
import model.OSMgraph.Node;
import model.mobility.MobilityEngine;
import model.parameters.Globals;
import utils.tracestool.Utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/**
 * This class has a batch of functions useful for dynamic routes calculations.
 * Also, it has a reference to car's hashmap of streets cost (each car
 * has its own perspective about the streets costs)
 *
 */

public class StreetsCostSharing {

//    pairs between each way id and its current cost
//    each car has its representation
    protected HashMap<Long, Double> streetGraphCost;

    protected HashMap<Long, VotingStreetCostData> streetUpdates;
    public static int MAX_DYNAMIC_VOTING_THRESHOLD = 7;
    public static int MIN_DYNAMIC_VOTING_THRESHOLD = 2;

    // percentage threshold for considering a street cost updated
    public static double STREET_COST_UPDATE_THRESHOLD = 0.4;

    /*  street update constraints: CAR_MIN_TRACE_ON_STREET - percent
    * CAR_MIN_TIME_ON_STREET - timestamps*/
    public static double CAR_MIN_TRACE_ON_STREET = 0.5;
    public static double CAR_MIN_TIME_ON_STREET = 10;

    MobilityEngine mobilityEngine;
    GeoCar car;


    public HashMap<Long, VotingStreetCostData> getStreetUpdates() {
        return streetUpdates;
    }

    public StreetsCostSharing(GeoCar car){
        this.mobilityEngine = MobilityEngine.getInstance();
        streetGraphCost = new HashMap<Long, Double>();
        this.streetUpdates = new HashMap<Long, VotingStreetCostData>();
        this.car = car;
    }

    /* addNewUpdate and removeOutdatedInfo are functions for
    * handling the updates regarding streets' costs by adding new costs
    * or removing a info that already has been scattered*/

    public void addNewUpdate(Long wayId, Double cost, Long discovererStamp) {

        if (!this.streetUpdates.containsKey(wayId)) {
            this.streetUpdates.put(wayId, new VotingStreetCostData(cost, this.car.getId()));
            return;
        }

        VotingStreetCostData votingStreetCostData = this.streetUpdates.get(wayId);
        votingStreetCostData.addDiscovererStamp(cost, discovererStamp);
    }

    /*The network is aware of this street costs. So they can be deleted from the
    * sharing package (streetUpdates).
    * There is no point in trying to achieve a number of votes for this cost. Given the fact
    * that the network is aware of it, the car will receive a valid package in the near future*/
    public void removeOutdatedInfo(HashMap<Long, VotingStreetCostData> outdatedCosts) {
        Iterator<Long> itr = outdatedCosts.keySet().iterator();
        long currentWayId;
        double currentUpdateCost;
        double outdatedCost;

        while (itr.hasNext()) {
            currentWayId = itr.next();

            currentUpdateCost = streetUpdates.get(currentWayId).getStreetCost();
            outdatedCost = outdatedCosts.get(currentWayId).getStreetCost();

            /*check if the currentUpdateCost has been modified before receiving this update
             * regarding outdated cost. So, if the difference between this 2 costs is under THRESHOLD,
             * there has been an update over this cost so it is not outdated anymore*/
            if (Math.abs(currentUpdateCost - outdatedCost) <
                    (currentUpdateCost * StreetsCostSharing.STREET_COST_UPDATE_THRESHOLD)) {
                streetUpdates.remove(currentWayId);
            }
        }
    }

/*  returns the cost of street if it knows, otherwise calculate*/
    public double getWayCost(Way way) {
        double streetCost;

        /*if the car does not know the street cost, take the first and the last node
        * of the street. Calculate the distance between them using normal cruising speed.*/
        if (!streetGraphCost.containsKey(way.id)) {
            Node firstNode = way.nodes.firstElement();
            Node lastNode = way.nodes.lastElement();

            double streetLength = Utils.getRealDistanceAB(way, firstNode.id, lastNode.id);
            streetCost = streetLength / (way.getMaximumSpeed() - 3);
            streetGraphCost.put(way.id, streetCost);

        } else {
            streetCost = streetGraphCost.get(way.id);
        }
        return streetCost;
    }


    /* this function calculates the average street cost considering a car that traveled just a segment
    * of the entire street. We cannot assume that the distance traveled by the car is the
    * actual street length*/
    public double calculateAverageCost (Way way, Node startingNode, Node finishNode, long wayStartTime, long wayFinishTime) {
        Node firstNode = way.nodes.firstElement();
        Node lastNode = way.nodes.lastElement();
        long carTraceTime = wayFinishTime - wayStartTime;

        double streetLength = Utils.getRealDistanceAB(way, firstNode.id, lastNode.id);

//        car's trace on the current street
        double carTraceLength = Utils.getRealDistanceAB(way, startingNode.id, finishNode.id);

//        start node is not correct
        if (carTraceLength > streetLength) {
            return -1;
        }

        if (carTraceLength < (StreetsCostSharing.CAR_MIN_TRACE_ON_STREET * streetLength) ||
        carTraceTime < StreetsCostSharing.CAR_MIN_TIME_ON_STREET) {
            return -1;
        }
        double speed = carTraceLength / carTraceTime;

        if (speed == 0) {
            return -1;
        }

        return streetLength / speed;
    }


    /* this method is called when a car changes the street. So it is able to generate a report */
    public void discoverNewWayCost(Way way, Node startingNode, Node finishNode, long startTime, long finishTime) {

        double reportedCost = calculateAverageCost(way, startingNode, finishNode, startTime, finishTime);

        if (reportedCost == -1) {
            return;
        }

        double currentStreetCost = getWayCost(way);

//      use the percentage threshold to validate the new update
        if (Math.abs(currentStreetCost - reportedCost) >
                (currentStreetCost * StreetsCostSharing.STREET_COST_UPDATE_THRESHOLD)) {
            streetGraphCost.put(way.id, reportedCost);

            // add this new cost to the "StreetUpdates" in order to inform the next trafficLight
            this.addNewUpdate(way.id, reportedCost, this.car.getId());
        }

    }

    public static long counterMaliciousCosts = 0;
    public static long counterRealCosts = 0;

    /*function called when a the CarDynamicRoutingApp finds some validUpdates for this car*/
    public void updateWayCost(long wayId, VotingStreetCostData receivedVotingData) {

        /*stamps management*/
        if (Globals.useVotingSystem) {
            if (!this.streetUpdates.containsKey(wayId)) {
                this.streetUpdates.put(wayId, receivedVotingData);
            } else {

                VotingStreetCostData currentVotingData = this.streetUpdates.get(wayId);
                currentVotingData.addNewStamps(receivedVotingData);
            }

            VotingStreetCostData votingStreetCostData = this.streetUpdates.get(wayId);
            if (votingStreetCostData.isStreetCostUpdated()) {

                if (car.getCurrentRoute().getWayIdsSet().contains(wayId)) {

                    if (VotingStreetCostData.isTheSameStreetCost(receivedVotingData.getStreetCost(), StreetsCostSharingMalicious.DECREASED_VALUE)
                            || (VotingStreetCostData.isTheSameStreetCost(receivedVotingData.getStreetCost(), StreetsCostSharingMalicious.INCREASED_VALUE))) {
                        synchronized (StreetsCostSharing.class) {
                            counterMaliciousCosts++;
                        }
                    } else {
                        synchronized (StreetsCostSharing.class) {
                            counterRealCosts++;
                        }
                    }

                    if (receivedVotingData.getStreetCost() > this.getWayCost(mobilityEngine.getWay(wayId))) {
                        double oldCost = this.getWayCost(mobilityEngine.getWay(wayId));
                        this.streetGraphCost.put(wayId, receivedVotingData.getStreetCost());
                        this.streetUpdates.put(wayId, receivedVotingData);

                        car.appointForRouteRecalculation( receivedVotingData.getStreetCost()
                                - oldCost);
                    }
                }

                // clear the voting session and redistribute the new info
                votingStreetCostData.clearVoteSession();
                this.streetUpdates.put(wayId, votingStreetCostData);

                /* the cost was voted, so we can add the cost to the KB*/
                this.streetGraphCost.put(wayId, votingStreetCostData.getStreetCost());

            } else {
                /*otherwise, we have to wait until the cost is voted*/
            }
        } else {
            // do not use voting system

            if (car.getCurrentRoute().getWayIdsSet().contains(wayId)) {

                if (VotingStreetCostData.isTheSameStreetCost(receivedVotingData.getStreetCost(), StreetsCostSharingMalicious.DECREASED_VALUE)
                || (VotingStreetCostData.isTheSameStreetCost(receivedVotingData.getStreetCost(), StreetsCostSharingMalicious.INCREASED_VALUE))) {
                    synchronized (StreetsCostSharing.class) {
                        counterMaliciousCosts++;
                    }
                } else {
                    synchronized (StreetsCostSharing.class) {
                        counterRealCosts++;
                    }
                }

                if (receivedVotingData.getStreetCost() > this.getWayCost(mobilityEngine.getWay(wayId))) {
                    double oldCost = this.getWayCost(mobilityEngine.getWay(wayId));
                    this.streetGraphCost.put(wayId, receivedVotingData.getStreetCost());
                    this.streetUpdates.put(wayId, receivedVotingData);

                    car.appointForRouteRecalculation( receivedVotingData.getStreetCost()
                            - oldCost);
                }
            } else {
                this.streetGraphCost.put(wayId, receivedVotingData.getStreetCost());
                this.streetUpdates.put(wayId, receivedVotingData);
            }
        }

    }

    public double calculateEntireRouteCost(HashSet<Long> wayIdsSet) {
        Iterator<Long> it = wayIdsSet.iterator();
        long currentWayId = 0;
        double total = 0;
        double currentCost = 0;

        while (it.hasNext()) {
            currentWayId = it.next();
            currentCost = this.getWayCost(mobilityEngine.getWay(currentWayId));
            total += currentCost;
        }
        return total;
    }
}
