package model;

import model.OSMgraph.Node;
import model.OSMgraph.Way;
import model.mobility.MobilityEngine;
import application.streetCostSharing.StreetsCostSharing;
import utils.Pair;
import utils.tracestool.Utils;

import java.util.*;

public class DynamicRoutes {
    public TreeMap<Long, Way> streetsGraph;
    MobilityEngine mobilityEngine;
    StreetsCostSharing streetsCostSharing;

    DynamicRoutes(StreetsCostSharing dr_utils) {
        mobilityEngine = MobilityEngine.getInstance();
        streetsGraph = mobilityEngine.streetsGraph;
        streetsCostSharing = dr_utils;
    }


    public Pair<List<Node>, HashSet<Long>> findPath(Node startNode, Node stopNode) {
        List<Node> intersectionsList = new ArrayList<Node>();
        TreeMap<Pair<Long,Long>,Node> path = new TreeMap<Pair<Long,Long>,Node>();
        TreeMap<Pair<Long,Long>,Double> distance = new TreeMap<Pair<Long,Long>,Double>();
        HashSet<Long> wayIdsSet = new HashSet<Long>();

        /*checks for startNode and stopNode correctness. +
        * if case, try to correct them by considering the closest nodes*/

        int ok = 1;
        if( startNode.id != -1 ) {
            Way sW =  streetsGraph.get(startNode.wayId);
            if(sW != null && sW.neighs.get(startNode.id) != null ) {
                startNode = sW.getNode(startNode.id);
            } else {
                ok = 0;
            }

        } else {
            ok = 0;
        }
        if (ok == 0) {
            startNode = Utils.getClosestJointFromCrtPosition(
                    streetsGraph, startNode.wayId,
                    streetsGraph.get(startNode.wayId).getClosestNode( startNode.lat, startNode.lon)
            );
        }

        ok = 1;
        if( stopNode.id != -1 ) {
            Way sW =  streetsGraph.get(stopNode.wayId);
            if(sW != null && sW.neighs.get(stopNode.id) != null ) {
                stopNode = sW.getNode(stopNode.id);
            } else {
                ok = 0;
            }

        } else {
            ok = 0;
        }
        if (ok == 0) {
            stopNode = Utils.getClosestJointFromCrtPosition(
                    streetsGraph, stopNode.wayId,
                    streetsGraph.get(stopNode.wayId).getClosestNode( stopNode.lat, stopNode.lon)
            );
        }

        if( startNode == null || stopNode == null ) {
            return null;
        }

        /* start the path finding algorithm*/

        Pair<Long,Long> currentPair = new Pair<Long,Long>(startNode.id, startNode.wayId);
        LinkedList<Pair<Long,Long>> q = new LinkedList<Pair<Long,Long>>();
        Node currentNode, jointNode;
        Way currentWay;
        Vector<Pair<Long,Long>> neighs = new Vector<Pair<Long,Long>>();
        HashMap<Node, Node> parents = new HashMap<Node, Node>();
        double currentDistance, parentDistance;

        q.addLast(currentPair);
        distance.put(currentPair, (double) 0);

        /*explore the graph in a bfs manner*/
        ok = 1;
        while(!q.isEmpty()) {
            if (currentPair.getFirst() == stopNode.id && currentPair.getSecond() == stopNode.wayId) {
                break;
            }
            currentWay = streetsGraph.get(currentPair.getSecond());
            currentNode = currentWay.getNode(currentPair.getFirst());

//            make sure that startNode has the same way id with what I am looking  for at the backpropagation
//            through parents list
            if (ok == 1) {
                startNode = currentNode;
                ok = 0;
            }

//           intersection's neighbours
            neighs = Utils.getDirectLinkedJointsFromCrtPosition(streetsGraph, currentNode);

            for( Pair<Long,Long> entry : neighs ) {
                jointNode = streetsGraph.get(entry.getSecond()).getNode(entry.getFirst());
                currentWay = streetsGraph.get(currentNode.wayId);
                if( !q.contains(entry) )
                {

                    /*calculate the distance between current and next node.
                    * add the distance from the source.
                    * the cost is measured in seconds*/
                    boolean isDistanceUpdated = false;
                    parentDistance = distance.get(currentPair);
                    currentDistance = parentDistance + streetsCostSharing.getWayCost(currentWay);

                    /*the nodes are explored in 2 scenarios:
                    * - the node has not been visited before
                    * - it has been visited, but there is a time improvement in reaching that node
                    * otherwise, the node will not be explored */

                    if (distance.containsKey(entry)) {

                        if (currentDistance < distance.get(entry)) {
                            distance.put(entry, currentDistance);
                            isDistanceUpdated = true;
                        }

                    } else {
                        distance.put(entry, currentDistance);
                        isDistanceUpdated = true;
                    }

                    if (isDistanceUpdated) {
                        q.addLast(new Pair<Long, Long>(jointNode.id, jointNode.wayId));
                        parents.put(jointNode, currentNode);
                    }
                }
            }
            currentPair = q.poll();
        }

        // build the new path
        try {

            currentNode = streetsGraph.get(currentPair.getSecond()).getNode(currentPair.getFirst());

            while (currentNode.id != -1 && currentNode.id != startNode.id) {

                intersectionsList.add(0, parents.get(currentNode));
                currentNode = parents.get(currentNode);
                wayIdsSet.add(currentNode.wayId);
            }

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        return new Pair<>(intersectionsList, wayIdsSet);

    }


}
