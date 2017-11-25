package application.routing;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;
import java.util.logging.Logger;

import controller.network.NetworkInterface;
import controller.network.NetworkType;
import controller.network.NetworkWiFi;
import controller.newengine.SimulationEngine;
import utils.tracestool.Utils;
import utils.tracestool.algorithms.OSMGraph;
import model.GeoCar;
import model.GeoCarRoute;
import model.MapPoint;
import model.OSMgraph.Node;
import model.OSMgraph.Way;
import model.mobility.MobilityEngine;
import model.network.Message;
import model.network.MessageType;
import application.Application;
import application.ApplicationType;

public class RoutingApplicationCar extends Application {

	private final static Logger logger = Logger.getLogger(RoutingApplicationCar.class.getName());

	/* The type of application  */
	private ApplicationType type = ApplicationType.ROUTING_APP;

	/* Reference to the car object */
	GeoCar car;

	/* If it demands a route or not */
	public boolean isActive = false;

	public List<MapPoint> originalRoute;

	public List<MapPoint> newRoute;

	public Vector<MapPoint> oldPoses = new Vector<MapPoint>();
	
	public static TreeMap<Long, Long> streetVisits = new TreeMap<Long, Long>();
	//public static TreeMap<Long, Double> streetCongestionD = new TreeMap<Long, Double>();

	public static TreeMap<Long, TreeMap<Long,Double>> streetsCost = new TreeMap<Long, TreeMap<Long,Double>>();

	public RoutingApplicationCar( GeoCar car ){
		this.car = car;
		initializeStreetsCostGraph();
	}

	@Override
	public boolean getStatus() {
		return isActive;
	}

	public void detectNewRoute()
	{
		 GeoCarRoute oldroute = car.getCurrentRoute();
		 MapPoint startNd = car.getCurrentPos();
		 MapPoint endNd = oldroute.getEndPoint();

		 if( startNd == null || endNd == null || startNd.equals(endNd) )
		 {
			 System.out.println(car.getId());
			 return;
		 }

//		 GeoCarRoute newroute = MobilityEngine.getInstance().FindPath( MobilityEngine.getInstance().streetsGraph, startNd, endNd, RoutingApplicationParameters.maxdepth );
//		 System.out.println(car.getId() + " " +newroute);
//		 if( newroute != null )
//		 {
//
//			 if( newroute.getIntersectionList().size() != 0 )
//			 {
//				 MobilityEngine.getInstance().updateIntersectionListWithNodes(newroute.getIntersectionList());
//				 car.replaceRoute(0, newroute);
//			 }
//		 
//		 }
	 
	}
	public Node lastIntersection = null;
	public Long lastStreet = -1l;
	@Override
	public String run() {
		String logs = "";
		boolean ok = false;
		/* distance before intersection */
		double max_distance = 3;
		Double g = 0d;
		Node intersection = new Node(-1, -1, -1);
		NetworkInterface net = car.getNetworkInterface(NetworkType.Net_WiFi);
		ArrayList<NetworkInterface> discoversServerset = ((NetworkWiFi)net).discoversServers();

		if( car.getCurrentPos() != null && lastStreet != car.getCurrentPos().wayId )
		{
			Long crtNb = streetVisits.get(car.getCurrentPos().wayId);
			streetVisits.put(car.getCurrentPos().wayId, crtNb == null ? 1: crtNb + 1);
			lastStreet = car.getCurrentPos().wayId;
		}

		if(car.getCurrentPos() != null)
		{
//			g = streetCongestionD.get(car.getCurrentPos().wayId);
//			streetCongestionD.put(car.getCurrentPos().wayId, g == null ? car.getCongestionDegree(): (car.getCongestionDegree() + g)/2);
			MapPoint crtPos = car.getCurrentPos();
			MapPoint oldPos = oldPoses.size() > 0 ? oldPoses.lastElement() : null;

			long crtTime = SimulationEngine.getInstance().getSimulationTime();
			crtPos.timestamp = new Date(crtTime * 1000);
			if( oldPos != null && oldPos.wayId != crtPos.wayId )
			{
				/* If the cars has only point on a street then it will be computed the AVG speed until the current point
				 * and then will be calibrate to common intersection.
				 * Otherwise, if the point is too far from current point is not a direct neighbor, the maximum speed according
				 * to local legislation will be considered.
				 */
				Node jointPoint = OSMGraph.getIntersectNode(oldPos.wayId, crtPos.wayId, MobilityEngine.getInstance().streetsGraph);
				if( jointPoint != null )
				{

					double distBJoint = Utils.distance(	oldPos.lat, oldPos.lon, jointPoint.lat, jointPoint.lon );
					double distAJoint = Utils.distance( jointPoint.lat, jointPoint.lon, crtPos.lat,crtPos.lon );

					double deltaTimeoldPoscrtPOs = (crtPos.timestamp.getTime() - oldPos.timestamp.getTime())/1000;

					double AVGspeed = 0.0d;

					MapPoint oldPos0 = oldPoses.get(0);
					double streetDistance = oldPoses.size() > 1 ? Utils.distance( oldPos.lat, oldPos.lon, oldPos0.lat, oldPos0.lon ):0;
					double deltaTime = oldPoses.size() > 1 ? 
									  (oldPos.timestamp.getTime() - oldPos0.timestamp.getTime())/1000:
									  0;

					/* compute the time used to cross the distance between oldPos and jointPoint */
					double timeUntilJoint = distBJoint/((distBJoint + distAJoint)/deltaTimeoldPoscrtPOs);

					deltaTime += timeUntilJoint;
					AVGspeed = (streetDistance + distBJoint)/deltaTime;

					Double maxSpeed = MobilityEngine.getInstance().streetsGraph.get(oldPos.wayId).getMaximumSpeed();

					g = car.getCongestionDegree(AVGspeed, maxSpeed);

					Message msg = new Message(car.getId(), discoversServerset.get(0).getOwner().getId(), null, MessageType.REQUEST_ROUTE_UPDATE, ApplicationType.ROUTING_APP);
					RoutingApplicationData data = new RoutingApplicationData("Route Request from car " + car.getId(), g, oldPos.wayId, crtPos.wayId, jointPoint.id, crtTime);

					
					data.route = car.getCurrentRoute();
					data.avgspeed = AVGspeed;
					data.setStartEndpoint(crtPos, data.route.getEndPoint());
					msg.setPayload(data);
					
					net.putMessage(msg);
					//System.out.println( car.getId() + "(" + oldPos.wayId +", "+ crtPos.wayId+") are neighbors having the speed "+ AVGspeed + " - " +MobilityEngine.getInstance().streetsGraph.get(oldPos.wayId).getMaximumSpeed() );
				}
				else
				{
					double AVGspeed = 0.0d;

					MapPoint oldPos0 = oldPoses.get(0);
					double streetDistance = oldPoses.size() > 1 ? Utils.distance( oldPos.lat, oldPos.lon, oldPos0.lat, oldPos0.lon ):0;
					double deltaTime = oldPoses.size() > 1 ? 
									  (oldPos.timestamp.getTime() - oldPos0.timestamp.getTime())/1000:
									  0;

					if( streetDistance != 0 && deltaTime != 0 )
					{
						AVGspeed = (streetDistance)/deltaTime;
					
						Double maxSpeed = MobilityEngine.getInstance().streetsGraph.get(oldPos.wayId).getMaximumSpeed();
						AVGspeed = AVGspeed == 0 ? maxSpeed : AVGspeed;

						g = car.getCongestionDegree(AVGspeed, maxSpeed);

						/* here g is zero, so will decrease the congestion average,
						 * so it will be ignored.
						 */
						Message msg = new Message(car.getId(), discoversServerset.get(0).getOwner().getId(), null, MessageType.REQUEST_ROUTE_UPDATE, ApplicationType.ROUTING_APP);
						RoutingApplicationData data = new RoutingApplicationData("Route Request from car " + car.getId(), g, oldPos.wayId, crtPos.wayId, -1, crtTime);

						
						data.route = car.getCurrentRoute();
						data.setStartEndpoint(crtPos, data.route.getEndPoint());
						data.avgspeed = AVGspeed;
						msg.setPayload(data);
						
						net.putMessage(msg);

					//	System.out.println( car.getId() + "(" + oldPos.wayId +", "+ crtPos.wayId+") are not neughbors" );
					}
				}

				/* TODO: here the cost should be sent to server */
				TreeMap<Long,Double> virtualNodes = streetsCost.get(oldPos.wayId);
				Double oldg = virtualNodes.get(crtPos.wayId);
				if( oldg != null )
				{
					virtualNodes.put(crtPos.wayId, oldg == 0 ? g : ( oldg + g )/2 );
					streetsCost.put( oldPos.wayId, virtualNodes );
				}

				oldPoses.clear();
	
			}
			else
			{
				if( oldPoses.size() > 1 )
				{

					oldPoses.remove( oldPoses.size() - 1 );
				}
				oldPoses.add(crtPos);
			}
		}

		if( car.getCurrentPos() != null && MobilityEngine.getInstance().isIntersectionAhead(car, max_distance, intersection) )
		{
			if( car.getCurrentRoute() != null && intersection != null ){
				ok = (lastIntersection == null ) || (( lastIntersection != null ) && ( lastIntersection.id != intersection.id )) ;
				if( ok )
				{
					//detectNewRoute();
					lastIntersection = intersection;
				}
			}
		}

		net.processOutputQueue();

		return logs;
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

	@Override
	public void process(Message m) {
		//System.out.println("The message received type is " + m.getType());

		if( m.getType() == MessageType.NEW_ROUTE_UPDATE )
		{
			RoutingApplicationData data = (RoutingApplicationData)m.getPayload();
			 if( data.route != null )
			 {

				 if( data.route.getIntersectionList().size() != 0 )
				 {
					 System.out.println("Setting a new route");
					 MobilityEngine.getInstance().updateIntersectionListWithNodes(data.route.getIntersectionList());
					 car.replaceRoute(0, data.route);
				 }
			 
			 }
		}
	}

	@Override
	public String stop() {
		return null;
	}
	public static void stopGlobalApplicationActions(){
		PrintWriter writer = null;
		try {
			writer = new PrintWriter("routingapp_statistics.txt", "UTF-8");
			writer.println("#streetID cars_that_crossed_this_street");
			for( Map.Entry<Long, Long> entry : streetVisits.entrySet() )
			{
				writer.println(entry.getKey() +" "+entry.getValue());	
			}

			writer.close();
			writer = new PrintWriter("routingapp_congestion_statistics.txt", "UTF-8");
			writer.println("#streetID congestion_degree");
			for( Map.Entry<Long, TreeMap<Long,Double>> entry : streetsCost.entrySet() )
			{
				writer.println(entry.getKey() +" :" );
				for( Map.Entry<Long, Double> costEntry : entry.getValue().entrySet() )
				{
					writer.println(costEntry.getKey() +" "+costEntry.getValue() );
				}
			}

			writer.close();

		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		}


	}
	public static void initializeStreetsCostGraph()
	{

		TreeMap<Long, Way> streetsGraph = MobilityEngine.getInstance().streetsGraph;
		/* iterate over each street and detect the output */
		for( Long streetID:streetsGraph.keySet() )
		{
			Way tmpSt = streetsGraph.get(streetID);

			if( tmpSt == null )
			{
				logger.info( streetID + "there is not presented in the graph of Streets \n");
				continue;
			}

			/*  obtain the output streets */
			Vector<Long> outstreetsIDs = tmpSt.getAllOutNeighbors(streetsGraph);

			TreeMap<Long,Double> costs = new TreeMap<Long,Double>();

			for( Long neighStreetID : outstreetsIDs)
			{
				costs.put(neighStreetID, 0d);
			}

			streetsCost.put( tmpSt.id, costs);

		}
	}
}
