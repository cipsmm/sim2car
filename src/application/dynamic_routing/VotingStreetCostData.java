package application.dynamic_routing;

import application.streetCostSharing.StreetsCostSharing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;


public class VotingStreetCostData {
    double streetCost;
    boolean firstVotingSession;

    /*each cost should be stamped by multiple entities before taking it into consideration.
    * there could be multiple costs reported for the same street, so we need
    * to keep track of each cost ant its stamps in order to make the best updated decision*/
    HashMap<Double, HashSet<Long>> costVotingMap;

    public int DYNAMIC_VOTING_THRESHOLD = 2;

    public HashMap<Double, HashSet<Long>> getCostVotingMap() {
        return costVotingMap;
    }

    public void setCostVotingMap(HashMap<Double, HashSet<Long>> costVotingMap) {
        this.costVotingMap = costVotingMap;
    }

    public VotingStreetCostData(double cost, long discovererStamp) {
        this.costVotingMap = new HashMap<Double, HashSet<Long>>();
        HashSet<Long> stamps = new HashSet<Long>();
        stamps.add(discovererStamp);
        this.costVotingMap.put(cost, stamps);
        this.streetCost = cost;
        this.firstVotingSession = true;
    }

    public double getStreetCost() {
        return streetCost;
    }

    public void setStreetCost(double streetCost) {
        this.streetCost = streetCost;
    }

    /** make sure to add a stamp to the correct cost
     * if a similar cost is not found, create another slot*/
    public void addDiscovererStamp(double cost, Long stamp) {
        HashSet<Long> stamps;
        Iterator<Double> it = costVotingMap.keySet().iterator();

        while(it.hasNext()) {
            double currentCost = it.next();

            if (VotingStreetCostData.isTheSameStreetCost(currentCost, cost)) {
                stamps = costVotingMap.get(currentCost);
                stamps.add(stamp);
                return;
            }
        }
        stamps = new HashSet<Long>();
        stamps.add(stamp);
        costVotingMap.put(cost, stamps);
    }

    public boolean isValidated(double cost, int votingThreshold) {

        if (costVotingMap.get(cost).size() >= votingThreshold) {
            return true;
        }
        return false;
    }


    public static boolean isTheSameStreetCost(double c1, double c2) {
        if (Math.abs(c1 - c2) <
                (c1 * StreetsCostSharing.STREET_COST_UPDATE_THRESHOLD)) {
            return true;
        }
        return false;
    }


    /** this method is useful in CarDynamicRoutingApp, at outdated checking. It returns
     * if there are new stamps in the param object for a specific cost*/
    public boolean hasNewStamps(VotingStreetCostData votingStreetCostData) {
        double paramCost = votingStreetCostData.getStreetCost();
        double costReference = -1;
        boolean hasTheCost = false;
        Iterator<Double> costIt = this.costVotingMap.keySet().iterator();

        /*check if the cost exists*/
        while (costIt.hasNext()) {
            double currentCost = costIt.next();

            if (VotingStreetCostData.isTheSameStreetCost(currentCost, paramCost)) {
                hasTheCost = true;
                /*keep the value of the current cost to refer the cost in the current Object
                * it could be slightly different*/
                costReference = currentCost;
                break;
            }
        }

        if (!hasTheCost) return false;

        Iterator<Long> it = votingStreetCostData.getCostVotingMap().get(paramCost).iterator();
        long currentStamp;

        /*check each stamp*/
        while (it.hasNext()) {
            currentStamp = it.next();

            if (!this.costVotingMap.get(costReference).contains(currentStamp)) {
                return true;
            }
        }
        return false;
    }


    /** add new costs with stamps, or add new stamps to the existing costs*/
    public void addNewStamps(VotingStreetCostData votingStreetCostData) {
        Iterator<Double> costIterator = votingStreetCostData.getCostVotingMap().keySet().iterator();
        Iterator<Double> currentSessionCostIterator = this.costVotingMap.keySet().iterator();

        while (costIterator.hasNext()) {
            double currentCost = costIterator.next();

            Iterator<Long> stampIterator = votingStreetCostData.getCostVotingMap().get(currentCost).iterator();
            long currentStamp;

            /*if the cost is not present into the Map, add the cost and its stamps from the param object*/
            while (currentSessionCostIterator.hasNext()) {
                double votedCost = currentSessionCostIterator.next();
                if (VotingStreetCostData.isTheSameStreetCost(votedCost, currentCost)) {
                    /*iterate through stamps and add the new ones*/
                    while (stampIterator.hasNext()) {
                        currentStamp = stampIterator.next();

                        if (!this.costVotingMap.get(votedCost).contains(currentStamp)) {
                            this.costVotingMap.get(votedCost).add(currentStamp);
                        }
                    }
                    return;
                }
            }

            /*if the cost not found in the session, add all stamps*/
            costVotingMap.put(currentCost, votingStreetCostData.getCostVotingMap().get(currentCost));
        }
    }

    /* check if there is a cost in the voting session that is greater than the threshold
    * there are 2 cases:
    * - an increased report
    * - a decreased report
    * The threshold is dynamically changed depending on the report type*/
    public boolean isStreetCostUpdated() {
        Iterator<Double> costIterator = this.costVotingMap.keySet().iterator();

        while (costIterator.hasNext()) {
            double currentCost = costIterator.next();

            if (currentCost == this.streetCost && !firstVotingSession) {
                continue;
            }

            /*first voting session*/
            if (this.firstVotingSession) {
                if (this.costVotingMap.get(currentCost).size() >= DYNAMIC_VOTING_THRESHOLD) {
                    this.streetCost = currentCost;
                    firstVotingSession = false;
                    return true;
                }

            } else if (currentCost > streetCost) {
                /*increased cost -> UPPER_THRESHOLD*/
                if (this.costVotingMap.get(currentCost).size() >= DYNAMIC_VOTING_THRESHOLD) {
                    this.streetCost = currentCost;

                    /*if another session wants to increase the cost more, it has to overwhelm a bigger threshold*/
                    if (DYNAMIC_VOTING_THRESHOLD < StreetsCostSharing.MAX_DYNAMIC_VOTING_THRESHOLD)
                        DYNAMIC_VOTING_THRESHOLD += 1;
                    return true;
                }
            } else {
                /*decreased cost -> LOWER_THRESHOLD*/
                if (this.costVotingMap.get(currentCost).size() >= DYNAMIC_VOTING_THRESHOLD) {
                    this.streetCost = currentCost;

                    if (DYNAMIC_VOTING_THRESHOLD > StreetsCostSharing.MIN_DYNAMIC_VOTING_THRESHOLD)
                        DYNAMIC_VOTING_THRESHOLD -= 1;
                    return true;
                }
            }
        }
        return false;
    }

    /* after a decision is made, there is no point in keeping the costs
    * that were not voted*/
    public void clearVoteSession() {
        this.costVotingMap.entrySet().removeIf(e -> e.getKey() != this.streetCost);
    }


    public void printVoteSession() {
        Iterator<Double> costIterator = this.costVotingMap.keySet().iterator();
        System.out.println("VOTE SESSION");
        while (costIterator.hasNext()) {
            double currentCost = costIterator.next();
            System.out.println("[" + currentCost + "] "  + this.costVotingMap.get(currentCost).toString());
        }
    }

    @Override
    public String toString() {
        return "VotingStreetCostData{" +
                "streetCost=" + streetCost +
                ", costVotingMap=" + costVotingMap.toString() +
                ", UPPER_DYNAMIC_VOTING_THRESHOLD=" + DYNAMIC_VOTING_THRESHOLD +
                '}';
    }
}
