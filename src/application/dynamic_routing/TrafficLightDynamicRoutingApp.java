package application.dynamic_routing;

import application.Application;
import application.ApplicationType;
import controller.network.NetworkInterface;
import controller.network.NetworkType;
import controller.network.NetworkWiFi;
import model.GeoTrafficLightMaster;
import model.OSMgraph.Node;
import model.OSMgraph.Way;
import model.mobility.MobilityEngine;
import model.network.Message;
import model.network.MessageType;
import utils.StreetsCostSharing;
import utils.tracestool.Utils;


import java.util.*;

public class TrafficLightDynamicRoutingApp extends Application {
    private boolean isActive;
    private GeoTrafficLightMaster trafficLightMaster;
    private ApplicationType type;
    private MobilityEngine mobilityEngine;
    private HashMap<Long, Double> streetGraphCost;
    private HashMap<Long, VotingStreetCostData> streetUpdatesToCars;
    private HashMap<Integer, NetworkInterface> closestTrafficLightsMap;
    private NetworkWiFi networkInterface;

    public static final int NORTH_INDEX = 0;
    public static final int SOUTH_INDEX = 1;
    public static final int EAST_INDEX = 2;
    public static final int WEST_INDEX = 3;



    public TrafficLightDynamicRoutingApp(boolean isActive, GeoTrafficLightMaster trafficLightMaster) {
        this.isActive = isActive;
        this.trafficLightMaster = trafficLightMaster;
        mobilityEngine = MobilityEngine.getInstance();
        type = ApplicationType.TRAFFIC_LIGHT_ROUTING_APP;
        streetGraphCost = new HashMap<Long, Double>();

        /*this structure has the role to keep track of the latest updates*/
        streetUpdatesToCars = new HashMap<Long, VotingStreetCostData>();
    }

    public void postConstructInit() {
        networkInterface = (NetworkWiFi) trafficLightMaster.getNetworkInterface(NetworkType.Net_WiFi);
        closestTrafficLightsMap = networkInterface.discoverClosestTrafficLightsGeographic();
    }


    public double getWayCost(Way way) {
        double streetCost;

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

    @Override
    public boolean getStatus() {
        return isActive;
    }

//    this function is called periodically from Simulation Engine (to send the messages from outputQueue)
    @Override
    public String run() {
        NetworkInterface net = trafficLightMaster.getNetworkInterface(NetworkType.Net_WiFi);
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

    private void sendUpdatesToCar(long destinationCarId) {
        Message message = new Message(this.trafficLightMaster.getId(), destinationCarId, this.streetUpdatesToCars.clone(),
                MessageType.TRAFFIC_LIGHT_INFORMS, ApplicationType.CAR_ROUTING_APP);

        this.networkInterface.putMessage(message);
    }


    private void spreadUpdatesToNeighbours(HashMap<Long, VotingStreetCostData> validUpdates, long sourceId){

        Iterator it = closestTrafficLightsMap.entrySet().iterator();
        long neighbourTrafficLightId;
        int neighbourDirection;
        Random rand = new Random();
        int stepOver1 = rand.nextInt(closestTrafficLightsMap.size());
        int stepOver2 = rand.nextInt(closestTrafficLightsMap.size());

        if (stepOver1 == stepOver2) {
            stepOver2 = (stepOver1 + 1) % closestTrafficLightsMap.size();
        }

        if (closestTrafficLightsMap.size() < 4) {
            stepOver2 = -1;
        }

        int i = -1;
        int sents = 0;
        while (it.hasNext()) {
            i++;
            Map.Entry pair = (Map.Entry) it.next();
            neighbourDirection = (int) pair.getKey();
            neighbourTrafficLightId = ((NetworkInterface) pair.getValue()).getOwner().getId();

            if (neighbourTrafficLightId == sourceId) {
                continue;
            }

//            if (i == stepOver1 || i == stepOver2) {
//                continue;
//            }

            /** to reduce the cpu consumption uncomment it, and comment the ones above*/
            if (i != stepOver1) continue;

            Message message = new Message(this.trafficLightMaster.getId(), neighbourTrafficLightId,
                    validUpdates.clone(), MessageType.TRAFFIC_LIGHT_INFORMS, ApplicationType.TRAFFIC_LIGHT_ROUTING_APP);
            message.setMessageDirection(neighbourDirection);
            sents++;

            this.networkInterface.putMessage(message);

        }
    }

    private void sendBackOutdatedUpdates(HashMap<Long, VotingStreetCostData> outdatedUpdates,
                                         long destinationId) {
        Message message = new Message(this.trafficLightMaster.getId(), destinationId, outdatedUpdates.clone(),
                MessageType.OUTDATED_COSTS, ApplicationType.CAR_ROUTING_APP);
        this.networkInterface.putMessage(message);
    }


    /* this function has the role to iterate through the reported costs and to
    * split them in valid data or outdated data (outdated data is useful only
    * in CARS communication context*/
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
            oldCost = getWayCost(mobilityEngine.getWay(currentWayId));
            updateCost = updates.get(currentWayId).getStreetCost();

            /*check if that cost is new and update, otherwise report it as outdated*/
            if (Math.abs(oldCost - updateCost) >
                    (oldCost * StreetsCostSharing.STREET_COST_UPDATE_THRESHOLD)) {
                streetGraphCost.put(currentWayId, updateCost);

                /*validUpdates will be useful when I'll add the validation policy*/
                validUpdates.put(currentWayId, updates.get(currentWayId));

                /*put the news in the structure responsible for sharing with the cars*/
                streetUpdatesToCars.put(currentWayId, updates.get(currentWayId));
            } else {
                /* inform the car that this message is outdated, so put the data into a map to be sent*/
                if (m.getType() == MessageType.CAR_INFORMS)
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
        HashMap<Long, VotingStreetCostData> validUpdates;
        HashMap<Long, VotingStreetCostData> outdatedUpdates;
        ArrayList<HashMap<Long, VotingStreetCostData>> aux;
        Iterator<Long> itr;

        switch (m.getType()) {
            case CAR_INFORMS:

                /* send back some updates*/
                if (!this.streetUpdatesToCars.isEmpty()) {
                    sendUpdatesToCar(m.getSourceId());
                }

                aux = updatesPreProcessing(m);
                validUpdates = aux.get(0);
                outdatedUpdates = aux.get(1);

                /* spread this update to the neighbours (traffic lights) and inform the car about outdated data*/
                if (!validUpdates.isEmpty()) {
                    spreadUpdatesToNeighbours(validUpdates, m.getSourceId());
                }

                if (!outdatedUpdates.isEmpty()) {
                    sendBackOutdatedUpdates(outdatedUpdates, m.getSourceId());
                }
                break;

            case TRAFFIC_LIGHT_INFORMS:

                aux = updatesPreProcessing(m);
                validUpdates = aux.get(0);

                /*it's not necessary to send outdated data because the traffic lights spread
                 * the message only to the closest neighbours. If the message is outdated,
                 * the current traffic light does not spread it anymore...
                 * */
                if (!validUpdates.isEmpty()) {
                    spreadUpdatesToNeighbours(validUpdates, m.getSourceId());
                }

                break;

            /* this type of message is only received from cars*/
            case OUTDATED_COSTS:

                /* remove outdated updates*/
                outdatedUpdates = (HashMap<Long, VotingStreetCostData>) m.getData();
                itr = outdatedUpdates.keySet().iterator();
                long currentWayId;
                double currentUpdateCost;
                double outdatedCost;

                while (itr.hasNext()) {
                    currentWayId = itr.next();
                    currentUpdateCost = streetUpdatesToCars.get(currentWayId).getStreetCost();
                    outdatedCost = outdatedUpdates.get(currentWayId).getStreetCost();

                    /*check if the currentUpdateCost has been modified before receiving this update
                    * regarding outdated cost. So, if the difference between this 2 costs is smaller than THRESHOLD,
                    * there has been an update over this cost so it is not outdated anymore*/
                    if (Math.abs(currentUpdateCost - outdatedCost) <
                            (currentUpdateCost * StreetsCostSharing.STREET_COST_UPDATE_THRESHOLD)) {
                        streetUpdatesToCars.remove(currentWayId);
                    }
                }
                break;
        }
    }
}
