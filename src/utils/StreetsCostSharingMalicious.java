package utils;

import model.GeoCar;
import model.OSMgraph.Node;
import model.OSMgraph.Way;
import model.mobility.MobilityEngine;

import java.util.ArrayList;
import java.util.Random;

public class StreetsCostSharingMalicious extends StreetsCostSharing {

    public static final double INCREASED_VALUE = 4000;
    public static final double DECREASED_VALUE = 1;
    public static final int REPORT_MULTIPLICATION_FACTOR = 10;
    ArrayList<Long> wayIdArr;

    public StreetsCostSharingMalicious(GeoCar car) {

        super(car);
         wayIdArr = new ArrayList<Long>(MobilityEngine.getInstance().streetsGraph.keySet());

    }

    @Override
    public void discoverNewWayCost(Way way, Node startingNode, Node finishNode, long startTime, long finishTime) {
        int counter = 0;
        Random rand = new Random();
        long wayId;

        switch (super.car.getPersonalityType()) {
            case MALICIOUS_RANDOM:

                while (counter < REPORT_MULTIPLICATION_FACTOR) {
                    int position = rand.nextInt(wayIdArr.size());
                    wayId = wayIdArr.get(position);

                    if (rand.nextInt(10) % 2 == 0)
                        super.addNewUpdate(wayId, StreetsCostSharingMalicious.INCREASED_VALUE, super.car.getId());
                    else
                        super.addNewUpdate(wayId, StreetsCostSharingMalicious.DECREASED_VALUE, super.car.getId());
                    counter++;
                }

                if (rand.nextInt(10) % 2 == 0) {
                    super.addNewUpdate(way.id, StreetsCostSharingMalicious.INCREASED_VALUE, super.car.getId());
                } else {
                    super.addNewUpdate(way.id, StreetsCostSharingMalicious.DECREASED_VALUE, super.car.getId());
                }
                break;

            case MALICIOUS_INCREASED:

                while (counter < REPORT_MULTIPLICATION_FACTOR) {
                    int position = rand.nextInt(wayIdArr.size());
                    wayId = wayIdArr.get(position);
                    super.addNewUpdate(wayId, StreetsCostSharingMalicious.INCREASED_VALUE, super.car.getId());
                    counter++;
                }
                super.addNewUpdate(way.id, StreetsCostSharingMalicious.INCREASED_VALUE, super.car.getId());
                break;

            case MALICIOUS_DECREASED:

                while (counter < REPORT_MULTIPLICATION_FACTOR) {
                    int position = rand.nextInt(wayIdArr.size());
                    wayId = wayIdArr.get(position);
                    super.addNewUpdate(wayId, StreetsCostSharingMalicious.DECREASED_VALUE, super.car.getId());
                    counter++;
                }
                super.addNewUpdate(way.id, StreetsCostSharingMalicious.DECREASED_VALUE, super.car.getId());
                break;
        }

    }

}
