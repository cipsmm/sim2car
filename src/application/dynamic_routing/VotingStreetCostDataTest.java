package application.dynamic_routing;


import java.util.HashMap;

public class VotingStreetCostDataTest {
    VotingStreetCostData votingStreetCostData;
    HashMap<Long, VotingStreetCostData> myMap = new HashMap<Long, VotingStreetCostData>();

    public void idTheSameCostTest() {
        System.out.println("---- IS THE SAME COST TEST.. ----");
    //   Considering Threshold = 0.2
        if (VotingStreetCostData.isTheSameStreetCost(9, 10)) {
            System.out.println("PASS");
        } else {
            System.out.println("FAIL");
        }

        if (VotingStreetCostData.isTheSameStreetCost(7, 10)) {
            System.out.println("FAIL");
        } else {
            System.out.println("PASS");
        }
        System.out.println();
    }

    public void addNewCostsTest() {
        System.out.println("---- ADD NEW COSTS TEST... ----");
        System.out.println("ADDING 22 and 14.5");
        votingStreetCostData = new VotingStreetCostData(22, 1);
        votingStreetCostData.addDiscovererStamp(10, (long) 5);
        votingStreetCostData.printVoteSession();
        if (!votingStreetCostData.isStreetCostUpdated()) {
            System.out.println("[PASS]: Not updated because there are not enough votes");
        } else {
            System.out.println("[FAIL]: at update 1");
        }

        System.out.println("ADDING 15");
        votingStreetCostData.addDiscovererStamp(11, (long) 4);
        votingStreetCostData.addDiscovererStamp(11, (long) 4);

        if (votingStreetCostData.isStreetCostUpdated()) {
            System.out.println("[PASS]: updated because there ARE enough votes");
        } else {
            System.out.println("[FAIL]: at update 2");
        }

        if (VotingStreetCostData.isTheSameStreetCost(votingStreetCostData.getStreetCost(), 11)) {
            System.out.println("[PASS]: Current cost correct = " + votingStreetCostData.getStreetCost());
        } else {
            System.out.println("[Fail]: wrong cost " + votingStreetCostData.getStreetCost());
        }

        System.out.println("ADDING 21 and 23");
        votingStreetCostData.addDiscovererStamp(21, (long) 2);
        votingStreetCostData.addDiscovererStamp(23, (long) 3);


        if (votingStreetCostData.isStreetCostUpdated()) {
            System.out.println("[PASS]: updated because there ARE enough votes");
        } else {
            System.out.println("[FAIL]: at update 2");
        }

        if (VotingStreetCostData.isTheSameStreetCost(votingStreetCostData.getStreetCost(), 21)) {
            System.out.println("[PASS]: Current cost correct = " + votingStreetCostData.getStreetCost());
        } else {
            System.out.println("[Fail]: wrong cost " + votingStreetCostData.getStreetCost());
        }

        if (votingStreetCostData.getCostVotingMap().size() == 2) {
            System.out.println("[PASS]: Number of costs is correct");
        } else {
            System.out.println("[FAIL]: WRONG: " + votingStreetCostData.getCostVotingMap().size() + " costs instead of 2");
        }
        System.out.println();
        System.out.println("SHOULD HAVE 3 VOTES for 22 AND 2 VOTES FOR 11");
        votingStreetCostData.printVoteSession();
        System.out.println();
        this.votingStreetCostData.clearVoteSession();
    }

    public void hasNewStampsTest() {
        System.out.println("---- HAS NEW STAMPS TEST... ----");
        VotingStreetCostData votingStreetCostDataAux = new VotingStreetCostData(50, 10);

        if (this.votingStreetCostData.hasNewStamps(votingStreetCostDataAux)) {
            System.out.println("[FAIL]");
        } else {
            System.out.println("[PASS]");
        }
        votingStreetCostData.addDiscovererStamp(15, (long) 4);

        votingStreetCostDataAux.addDiscovererStamp(14.5, (long) 14);
        votingStreetCostDataAux.addDiscovererStamp(15, (long) 15);

        /*update the the street cost after the last votes*/
        votingStreetCostDataAux.isStreetCostUpdated();

        if (this.votingStreetCostData.hasNewStamps(votingStreetCostDataAux)) {
            System.out.println("[PASS]");
        } else {
            System.out.println("[FAIL]");
        }
        votingStreetCostData.clearVoteSession();
        System.out.println();
    }

    public void addNewStampsTest() {
        System.out.println("---- ADD NEW STAMPS TEST ...---");
        votingStreetCostData.getCostVotingMap().clear();
        this.votingStreetCostData.addDiscovererStamp(15, (long) 4);
        this.votingStreetCostData.addDiscovererStamp(16, (long) 3);
        this.votingStreetCostData.addDiscovererStamp(15, (long) 2);
        this.votingStreetCostData.addDiscovererStamp(50, (long) 12);
        this.votingStreetCostData.addDiscovererStamp(90, (long) 11);

        VotingStreetCostData votingStreetCostDataAux = new VotingStreetCostData(50, 10);
        votingStreetCostDataAux.addDiscovererStamp(15, (long) 1);
        // cost discovered by the same car
        votingStreetCostDataAux.addDiscovererStamp(15, (long) 2);
        votingStreetCostDataAux.addDiscovererStamp(15, (long) 23);
        votingStreetCostDataAux.addDiscovererStamp(51, (long) 24);
        votingStreetCostDataAux.addDiscovererStamp(48, (long) 21);

        this.votingStreetCostData.addNewStamps(votingStreetCostDataAux);

        if (votingStreetCostData.getCostVotingMap().size() == 3) {
            System.out.println("[PASS]: Number of costs is correct");
        } else {
            System.out.println("[FAIL]: WRONG: " + votingStreetCostData.getCostVotingMap().size() + " costs instead of 3");
        }

        votingStreetCostData.printVoteSession();
        votingStreetCostData.isStreetCostUpdated();
        votingStreetCostData.clearVoteSession();
        System.out.println("Voted cost is: " + votingStreetCostData.getStreetCost());
        votingStreetCostData.printVoteSession();
        System.out.println();
    }

    public void testSomething() {
        VotingStreetCostData votingStreetCostData = new VotingStreetCostData(50, 10);
        myMap.put((long)1, votingStreetCostData);
        VotingStreetCostData v = myMap.get((long)1);
        v.addDiscovererStamp(20, (long)11);

        myMap.get((long)1).printVoteSession();

    }

    public void testIsStreetCostUpdated() {
        System.out.println("---- IS STREET COST UPDATED... ----");

        VotingStreetCostData votingStreetCostData = new VotingStreetCostData(50, 10);
        VotingStreetCostData votingStreetCostDataAux = new VotingStreetCostData(50, 10);

        votingStreetCostDataAux.addDiscovererStamp(48, (long)11);
        votingStreetCostDataAux.addDiscovererStamp(48, (long)12);
        votingStreetCostDataAux.addDiscovererStamp(49, (long)13);

        if (votingStreetCostData.isStreetCostUpdated()) {
            System.out.println("[FAIL]");
        } else {
            System.out.println("[PASS]");
        }

        votingStreetCostData.addNewStamps(votingStreetCostDataAux);

        if (votingStreetCostData.isStreetCostUpdated()) {
            System.out.println("[PASS]");
        } else {
            System.out.println("[FAIL]");
        }

        if (votingStreetCostData.UPPER_DYNAMIC_VOTING_THRESHOLD == 2) {
            System.out.println("PASS");
        } else {
            System.out.println("FAIL");
        }
        votingStreetCostData.clearVoteSession();
        if (votingStreetCostData.isStreetCostUpdated()) {
            System.out.println("[FAIL]");
        } else {
            System.out.println("[PASS]");
        }

        votingStreetCostDataAux.getCostVotingMap().clear();
        votingStreetCostDataAux.addDiscovererStamp(90, (long)111);
        votingStreetCostData.addNewStamps(votingStreetCostDataAux);

        if (votingStreetCostData.isStreetCostUpdated()) {
            System.out.println("[FAIL]");
        } else {
            System.out.println("[PASS]");
        }
        System.out.println("Voted cost is: " + votingStreetCostData.getStreetCost());

        votingStreetCostDataAux.addDiscovererStamp(92, (long)113);

        votingStreetCostData.addNewStamps(votingStreetCostDataAux);
        votingStreetCostData.printVoteSession();
        if (votingStreetCostData.isStreetCostUpdated()) {
            System.out.println("[PASS]");
        } else {
            System.out.println("[FAIL]");
        }
        System.out.println("Voted cost is: " + votingStreetCostData.getStreetCost());

        if (votingStreetCostData.UPPER_DYNAMIC_VOTING_THRESHOLD == 3) {
            System.out.println("PASS");
        } else {
            System.out.println("FAIL");
        }
    }

    public static void main(String[] args) {
        VotingStreetCostDataTest votingStreetCostDataTest = new VotingStreetCostDataTest();
        votingStreetCostDataTest.idTheSameCostTest();
        votingStreetCostDataTest.addNewCostsTest();
        votingStreetCostDataTest.hasNewStampsTest();
        votingStreetCostDataTest.addNewStampsTest();
        votingStreetCostDataTest.testIsStreetCostUpdated();
    }
}
