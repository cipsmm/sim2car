package application.dynamic_routing;

import application.Application;
import application.ApplicationType;
import controller.network.NetworkInterface;
import controller.network.NetworkType;
import model.GeoCar;
import model.mobility.MobilityEngine;
import model.network.Message;
import model.network.MessageType;
import model.parameters.Globals;
import application.streetCostSharing.StreetsCostSharing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class CarDynamicRoutingApp extends Application {

    private boolean isActive;
    private GeoCar car;
    private ApplicationType type;
    private MobilityEngine mobilityEngine;

    public CarDynamicRoutingApp(boolean isActive, GeoCar car, ApplicationType type) {
        this.isActive = isActive;
        this.car = car;
        this.type = type;
        this.mobilityEngine = MobilityEngine.getInstance();
    }

    @Override
    public boolean getStatus() {
        return false;
    }

    @Override
    public String run() {
        NetworkInterface net = car.getNetworkInterface(NetworkType.Net_WiFi);
        net.processOutputQueue();
        return "";
    }

    @Override
    public String stop() {
        return null;
    }

    @Override
    public String getInfoApp() {
        return null;
    }

    @Override
    public Object getData() {
        return null;
    }

    @Override
    public ApplicationType getType() {
        return type;
    }


    public void sendOutdatedDataToTrafficLight(HashMap<Long, VotingStreetCostData> outdated, long destinationTrafficLightId) {
        NetworkInterface networkInterface = this.car.getNetworkInterface(NetworkType.Net_WiFi);
        Message message = new Message(this.car.getId(), destinationTrafficLightId, outdated.clone(),
                MessageType.OUTDATED_COSTS, ApplicationType.TRAFFIC_LIGHT_ROUTING_APP);
        networkInterface.putMessage(message);
    }


    public void sendOutdatedDataToCar(HashMap<Long, VotingStreetCostData> outdated, long neighbourCarId) {
        NetworkInterface networkInterface = this.car.getNetworkInterface(NetworkType.Net_WiFi);
        Message message = new Message(this.car.getId(), neighbourCarId, outdated.clone(),
                MessageType.OUTDATED_COSTS, ApplicationType.CAR_ROUTING_APP);
        networkInterface.putMessage(message);
    }


    public boolean isOutdated(double oldCost, double updateCost, long currentWayId, VotingStreetCostData votingStreetCostData) {
        boolean returnValue = true;

        /* if the cost itself is new for the car's KB*/
        if (Math.abs(oldCost - updateCost) >
                (oldCost * StreetsCostSharing.STREET_COST_UPDATE_THRESHOLD)) {
            returnValue = false;
        }

        /*if the cost does not have any new stamp cases:
        * has new stamps -> not outdated
        * does not have -> is outdated
        * it is a new cost to share -> not outdated*/

        if (returnValue && Globals.useVotingSystem) {
            if (this.car.getStreetsCostSharing().getStreetUpdates().containsKey(currentWayId)) {
                boolean hasNewStamps = this.car.getStreetsCostSharing().getStreetUpdates()
                        .get(currentWayId).hasNewStamps(votingStreetCostData);
                returnValue = !hasNewStamps;
            } else {
                returnValue = false;
            }
        }

        return returnValue;
    }


    /* this function has the role to iterate through the reported costs and to
     * split them into valid data or outdated data (outdated data is useful only
     * in CARS communication context)*/
    private ArrayList<HashMap<Long, VotingStreetCostData>> updatesPreProcessing(Message m) {
        HashMap<Long, VotingStreetCostData> updates;
        HashMap<Long, VotingStreetCostData> validUpdates = new HashMap<Long, VotingStreetCostData>();
        HashMap<Long, VotingStreetCostData> outdatedUpdates = new HashMap<Long, VotingStreetCostData>();
        Iterator<Long> itr;

        updates = (HashMap<Long, VotingStreetCostData>) m.getData();
        itr = updates.keySet().iterator();
        long currentWayId;
        double oldCost, updateCost;

        while (itr.hasNext()) {
            currentWayId = itr.next();
            oldCost = car.getStreetsCostSharing().getWayCost(mobilityEngine.getWay(currentWayId));
            updateCost = updates.get(currentWayId).streetCost;

            /*check outdated*/
            if (!isOutdated(oldCost, updateCost, currentWayId, updates.get(currentWayId))) {

                car.getStreetsCostSharing().updateWayCost(currentWayId, updates.get(currentWayId));
                validUpdates.put(currentWayId, updates.get(currentWayId));

            } else {
                outdatedUpdates.put(currentWayId, updates.get(currentWayId));
            }
        }
        ArrayList<HashMap<Long, VotingStreetCostData>> aux = new ArrayList<HashMap<Long, VotingStreetCostData>>();
        aux.add(validUpdates);
        aux.add(outdatedUpdates);
        return aux;
    }


    @Override
    public void process(Message m) {
        ArrayList<HashMap<Long, VotingStreetCostData>> aux;
        HashMap<Long, VotingStreetCostData> outdated;

        switch (m.getType()) {
            case TRAFFIC_LIGHT_INFORMS:

                aux = updatesPreProcessing(m);
                outdated = aux.get(1);

                /*if there is some outdated data, inform the traffic light*/
                if (!outdated.isEmpty()) {
                    sendOutdatedDataToTrafficLight(outdated, m.getSourceId());
                }

                break;

            case CAR_INFORMS:
                /*some other car informs current car*/

                aux = updatesPreProcessing(m);
                outdated = aux.get(1);

                if (!outdated.isEmpty()) {
                    sendOutdatedDataToCar(outdated, m.getSourceId());
                }
                break;

            case OUTDATED_COSTS:

                HashMap<Long, VotingStreetCostData> outdatedUpdates = (HashMap<Long, VotingStreetCostData>) m.getData();
                this.car.getStreetsCostSharing().removeOutdatedInfo(outdatedUpdates);

                break;
        }
    }
}
