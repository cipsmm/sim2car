package model;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import controller.network.NetworkInterface;
import controller.newengine.SimulationEngine;
import model.OSMgraph.Node;
import model.OSMgraph.Way;
import model.mobility.MobilityEngine;
import model.parameters.Globals;
import model.personality.Personality;
import model.personality.RegularPersonality;
import utils.Pair;
import utils.TraceParsingTool;
import application.Application;

public class GeoCar extends Entity {
	
	private final Logger logger = Logger.getLogger(GeoCar.class.getName());

	/**
	 * Next position of the car - created by preparePosition() and its value
	 * will be checked against traffic rule and will replace currentPos.
	 */
	private MapPoint nextPos = null;

	/** The speed of the car. */
	private double speed = 0.0;

	/** If the Car still has points in the trace. */
	private int active = 1;

	/** The driver's personality */
	private Personality driver;
	
	/** The new list of routes of the car */
	private List<GeoCarRoute> routes;
	
	/** Reference to mobility */
	private MobilityEngine mobility;
	
	/** Whether the car has moved after preparing move */
	public AtomicBoolean hasMoved = new AtomicBoolean();
	
	private int seconds = 0;

	private long routeStartTime = 0;
	private long routes_idx = 0;
	
	/** Used to count the number of times the car has stayed still */
	private int still = 0;
	
	/**
	 * List of nodes between current position and next position.
	 * The list is sorted from the current position towards next position.
	 */
	public List<Node> nodesToMoveOver = null;

	private boolean beginNewRoute;

	/** The car in front of this car. Is updated at every iteration */
	private Pair<GeoCar, Double> carAhead = null;
	
	/** Route length in time information */
	StringBuffer routesTime = new StringBuffer();
//	StringBuffer tracesTime = new StringBuffer();
	
	public GeoCar(int id) {
		this(id, new RegularPersonality());
	}
	
	public GeoCar(int id, Personality driver) {
		super(id);
		this.mobility = MobilityEngine.getInstance();
		this.driver = driver;
		this.setCurrentPos(null);
		this.setNextPos(null);
		this.beginNewRoute = true;
	}

	public void setSpeed(double speed) {
		this.speed = speed;
	}

	public double getSpeed() {
		return speed;
	}

	public void setActive(int active) {
		this.active = active;
	}

	public int getActive() {
		return active;
	}

	public MapPoint getNextPos() {
		return nextPos;
	}

	public void setNextPos(MapPoint nextPos) {
		this.nextPos = nextPos;
	}
	
	public GeoCarRoute getCurrentRoute() {
		if( this.routes.size() != 0 )
			return this.routes.get(0);
		return null;
	}
	
	public void addRoute( int idx, GeoCarRoute newroute ) {
		if( idx < this.routes.size() )
			this.routes.add(idx, newroute);
	}
	
	public void replaceRoute( int idx, GeoCarRoute newroute ) {
		if( idx < this.routes.size() )
			this.routes.set(idx, newroute);
	}

	public void setRoutes(List<GeoCarRoute> routes) {
		this.routes = routes;
	}

	public String runApplications() {
		String result = "";
		for (Application application : this.applications) {
			result += application.run();
		}
		return result;
	}

	public String stopApplications() {
		String result = "";
		for (Application application : this.applications) {
			result += application.stop();
		}
		return result;
	}

	public String stopNetwork() {
		String result = "";
		for (NetworkInterface net : this.netInterfaces) {
			result += net.stop();
		}
		return result;
	}

	
	/**
	 * Compute and return the speed of the car for when the road is free. 
	 */
	public double computeSpeedFreeDriving() {
		double computedSpeed = speed;
		if (computedSpeed < driver.getWantedSpeed(this.getCurrentPos().wayId)) {
			computedSpeed = computedSpeed + driver.getAccelerationFreeDriving() * (double) Globals.timePeriod;
		}
		if (computedSpeed > driver.getWantedSpeed(this.getCurrentPos().wayId)) {
			computedSpeed = driver.getWantedSpeed(this.getCurrentPos().wayId);
		}
		return computedSpeed;
	}

	/**
	 * Compute and return the speed of the car when there's another car in front
	 * and this car needs to slow down. The necessary brake is computed so that
	 * the desired distance of the driver is obtained when the speed reaches
	 * the speed of the preceding vehicle.
	 */
	public double computeSpeedApproaching(double otherCarSpeed, double otherCarDistance) {
		double computedSpeed = 0.0;
		double accel = -(this.speed - otherCarSpeed) * (this.speed - otherCarSpeed)
				/ (2 * (otherCarDistance - driver.getDesiredDistance(otherCarSpeed)));

		accel /= 2.0; // correction, TODO: Why necessary?

		if (accel < 0) {
			/* When the driver has enough distance to slow down gradually,
			   the acceleration is negative. */
			computedSpeed = speed + accel * (double) Globals.timePeriod;
		} else {
			/* The driver is already closer to the front car than the desired
			   distance, so he has to brake as hard as possible. */
			computedSpeed = computeSpeedMaximumBrake();
		}
		if (computedSpeed > driver.getWantedSpeed(this.getCurrentPos().wayId)) {
			computedSpeed = driver.getWantedSpeed(this.getCurrentPos().wayId);
		}
		
		return computedSpeed;
	}

	/**
	 * Compute and return the speed of the car when approaching an intersection.
	 */
	public double computeSpeedApproachingIntersection() { 
		double accel = driver.getIntersectionDeceleration();
		double computedSpeed = speed + accel * (double) Globals.timePeriod;
		if (computedSpeed < driver.getWantedSpeed(this.getCurrentPos().wayId) / 2)
			computedSpeed = driver.getWantedSpeed(this.getCurrentPos().wayId) / 2;
		return computedSpeed;
	}

	/**
	 * Compute and return the speed of the car when it breaks as much as it can.
	 */
	public double computeSpeedMaximumBrake() {
		double accel = driver.getAccelerationMaximumBrake();
		return speed + accel * (double) Globals.timePeriod;
	}
	
	/** Update the speed of the car given the intersection or the car ahead. */
	public void updateSpeed() {
		if (carAhead != null) {
			GeoCar carInFront = carAhead.getFirst();
			Double distance = carAhead.getSecond();
			
			if (carInFront == null) {
				speed = computeSpeedFreeDriving();
			} else {
				if (distance >= this.driver.getInfluenceDistance(speed)) {
					speed = computeSpeedFreeDriving();
				} else if (distance < driver.getInfluenceDistance(speed) &&
						   distance >= driver.getSafetyDistance(speed)) {
					/* In between the two distances - the action depends on speed */
					if (carInFront.speed > this.speed) {
						speed = computeSpeedFreeDriving();
					} else if (carInFront.speed == this.speed) {
						if (carInFront.speed == 0.0) {
							/*
							 * practically stopped behind another stopped vehicle;
							 * it's safe to get a little closer
							 */
							speed = computeSpeedFreeDriving();
						}
						/* this car follows the other one; no change of speed */
					} else {
						/* should brake; */
						speed = computeSpeedApproaching(carInFront.speed, distance);
					}
				} else if (distance < driver.getSafetyDistance(speed)) {
					/* should brake */
					if (carInFront.speed < speed) {
						speed = computeSpeedMaximumBrake();
					}
				}
			}	
		}

		/**
		 * TODO(Cosmin): Analyze this section code if it can be activated.
		 */
//		if (mobility.isIntersectionAhead(this, driver.getInfluenceDistance(speed))) {
//			speed = computeSpeedApproachingIntersection();
//		}
	}

	/* After a car has crossed a street it will report the CongestionDegree of that
	 * street how it was perceived by it.
	 */
	public Double getCongestionDegree( Double AVGspeed, Double maxSpeed )
	{
		if( this.getCurrentPos() == null )
			return 0d;
		Double g = 0d;

		/* Adjust this parameter according to the experiment requirements */
		Double K = 1d, e = 0.001d;
		if( AVGspeed <= maxSpeed )
		{
			g = K * Math.exp( Math.log(e)*(AVGspeed/maxSpeed) );
		}

		return g;
	}
	/**
	 * Prepares the next position the car will go to.
	 * This must be called after the updateSpeed method.
	 */
	public MapPoint getNextPosition() {
		GeoCarRoute route = this.routes.get(0);
		MapPoint newPos = null;
		nodesToMoveOver = new LinkedList<Node>();
		double lat = this.getCurrentPos().lat;
		double lon = this.getCurrentPos().lon;
		double speed = this.speed;
		
		double distance = 0.0;

		if (this.getCurrentPos().equals(route.getEndPoint())) {
			long timei = SimulationEngine.getInstance().getSimulationTime() - routeStartTime;
			if(timei > 1 )
			{
				routesTime.append((routes_idx++) + " " + timei +"\n" );
			}
			return null;
		}

		Iterator<Node> it = route.getIntersectionList().iterator();
		Node prevNode = null;
		Node nextNode = mobility.getSegmentByIndex(this.getCurrentPos());
		int newDirection = this.getCurrentPos().direction;
		while (it.hasNext()) {
			prevNode = nextNode;
			nextNode = it.next();
			/* De reanalizat partea asta */
			if (prevNode.wayId != nextNode.wayId) {
				prevNode = mobility.getSegmentById(nextNode.wayId, prevNode.id);
				if (prevNode == null)
					/* TODO(mariana): e ceva gresit cu ruta asta, returnam null ca
					 * apoi sa i se faca skip
					 */
					return null;
			}

			/**
			 * TODO(Cosmin):
			 * Check if getDirection - does work for the street with 2 ways
			 */
			newDirection = mobility.getDirection(prevNode, nextNode);
			distance = TraceParsingTool.distance(lat, lon, nextNode.lat, nextNode.lon);
			
			if (distance <= speed) {
				speed -= distance;
				lat = nextNode.lat;
				lon = nextNode.lon;
				nodesToMoveOver.add(nextNode);
				it.remove();
			} else {
				lat = (nextNode.lat - lat) * speed / distance + lat;
				lon = (nextNode.lon - lon) * speed / distance + lon;
				break;
			}
		}
		
		MapPoint end = route.getEndPoint();
		/*
		 *  This can happen if the speed is larger than the distance between
		 *  the end point and the last element of the route, which is the first
		 *  node after the end point. In this case, the new position is after
		 *  that last intersection -> we move it on the end point and
		 *  add the intersection back to the list. 
		 */

		if (route.getIntersectionList().size() == 0) {
			route.getIntersectionList().add(nextNode);
			nodesToMoveOver.remove(nextNode);
			newPos = end;
		} else {
			/* 
			 * We are between the end point and the last intersection ->
			 * the car has arrived to the destination
			 */
			Node last = route.getIntersectionList().get(route.getIntersectionList().size() - 1);
			if (route.getIntersectionList().size() == 1 && 
					(mobility.isBetween(end.lat, end.lon, last.lat, last.lon, lat, lon))) {
					/* TODO(Cosmin): Test if the code ever reach this point */
					newPos = end;
			} else {
				newPos = MapPoint.getMapPoint(lat, lon,
											  this.getCurrentPos().occupied,
											  prevNode.wayId);
				newPos.segmentIndex = mobility.getSegmentNumber(prevNode);
				newPos.cellIndex = mobility.getCellIndex(prevNode, nextNode, newPos);
			}
		}
		
		newPos.direction = newDirection;
		
		if (newPos.equals(route.getEndPoint())) {
			long timei = SimulationEngine.getInstance().getSimulationTime() - routeStartTime;
			if(timei > 1 )
			{
				routesTime.append((routes_idx++) + " " + timei +"\n" );
			}
			return null;
		}
		
		return newPos;
	}

	/**
	 * Prepare next Move for this car object.
	 */
	public void prepareMove() {
		try {
			/**
			 * Hasn't started driving on a route, if it fails to add point to street
			 * give up and try at the next tick.
			 */
			if (this.getCurrentPos() == null) {
				setBeginNewRoute(true);
				initRoute();
				return;
			}
			
			carAhead = mobility.getCarAhead(this, driver.getInfluenceDistance(speed));
			updateSpeed();
//			System.out.println("speed: " + speed);
//			System.out.println("way: " + this.getCurrentPos().wayId);
			nextPos = getNextPosition();
			
			this.setBeginNewRoute(false);
			mobility.queueNextMove(this.getId(), nextPos);
			hasMoved.set(false);
		} catch (RuntimeException e) {
			/** Something was wrong with the route, so it's better to start a new one.*/
			setBeginNewRoute(true);
			long timei = SimulationEngine.getInstance().getSimulationTime() - routeStartTime;
			if(timei > 1 )
			{
				routesTime.append((routes_idx++) + " " + timei +"\n");
			}	
			initRoute();
			return;
		}
	}
	
	public void move() {
		try {
			if (hasMoved.compareAndSet(false, true) && this.active == 1) {
				if (carAhead != null && carAhead.getFirst() != null)
					carAhead.getFirst().move();
				
				GeoCar car = carAhead.getFirst();
				List<GeoCar> carsOnRoute = mobility.getCarsOnRoute(getCurrentPos(), getNextPos(), nodesToMoveOver);
				if (carsOnRoute.contains(car)) {
					Node prevNode = null;
					if (nodesToMoveOver.size() > 0)
						prevNode = nodesToMoveOver.get(nodesToMoveOver.size() - 1);
					mobility.removeQueuedCar(this.nextPos, this.getId());
					nextPos = mobility.getPointBefore(car.getCurrentPos(), prevNode);
					mobility.queueNextMove(this.getId(), this.nextPos);
				}
				
				/* E oare necesar sa avem aici o verificare pentru masinile care au
				 * atat current pos cat si next pos pe ruta mea ?
				 */
				
				List<Node> between = new LinkedList<Node>(this.nodesToMoveOver);
				List<Node> next = new LinkedList<Node>();
				MapPoint newPosition = mobility.moveCarInCell(this, this.getNextPos(), between, next);
				
				if (newPosition == null) {
//					System.out.println("here");
					resetRoute();
					return;
				}
				if (newPosition == this.getCurrentPos()) {
					if (still > 10) {
						/* we've stayed too much time in the same place */
						resetRoute();
						logger.warning("Car " + this.getId() + " has spent too much time in the same place. Reset route.");
						return;
					} else
						still++;
				} else
					still = 0;
	
				MapPoint oldNextPos = this.nextPos;
				if (newPosition != this.getNextPos()) {
					this.routes.get(0).getIntersectionList().addAll(0, next);
					this.nextPos = newPosition;
				}
	
				mobility.removeCar(this.getCurrentPos(), this.getId());
				mobility.removeQueuedCar(oldNextPos, this.getId());
				this.setCurrentPos(newPosition);
				mobility.addCar(this);
				finishMove();
			}
		} catch (RuntimeException e) {
			/** Something was wrong with the route, so it's better to start a new one.*/
			setBeginNewRoute(true);
			initRoute();
			return;
		}
	}

	public void initStartPosition() {

		if (routes == null || this.routes.size() == 0) {
			logger.warning("Car " + this.getId() + " has no routes!");
			return;
		}

		GeoCarRoute route = this.routes.get(0);
		MapPoint start = route.getStartPoint();
		MapPoint end = route.getEndPoint();
		List<Node> intersections = route.getIntersectionList();
		
		/* Add nodes before and after the start and end point top get a complete
		 * view of the route. This is needed before updating intersections with
		 * all intermediate nodes so that we get nodes between start point and
		 * the first one in the intersection.
		 */
		Node first = intersections.get(0);
		Node last = intersections.get(intersections.size() - 1);

		Node newIntersection = mobility.getNodeOnOppositeSide(start.wayId, start, first);
		if( newIntersection.id != intersections.get(0).id )
		{
			intersections.add(0, newIntersection);
		}

		Node newIntersection1 = mobility.getNodeOnOppositeSide(end.wayId, end, last);
		if( newIntersection1.id != intersections.get(intersections.size() - 1).id )
		{
			intersections.add( newIntersection1 );
		}

		mobility.updateIntersectionListWithNodes(intersections);

		/* Update start point to a point inside the source cell */
		first = intersections.get(0);
		Node second = intersections.get(1);
		start = mobility.getPointInCell(
				first, second, mobility.getCellIndex(first, second, start));
		if( start == null )
			return;
		route.setStartPoint(start);
		route.getStartPoint().occupied = start.occupied;

		/* Update end point to a point inside the destination cell */
		last = intersections.get(intersections.size() - 1);
		Node nextToLast = intersections.get(intersections.size() - 2);
		end = mobility.getPointInCell(
				nextToLast, last, mobility.getCellIndex(nextToLast, last, end));
		if( end == null )
			return;

		route.setEndPoint(end);

		route.getEndPoint().occupied = end.occupied;

		/* Delete the first node as it's not really part of the route, but
		 * was useful until now.
		 */
		intersections.remove(0);
		this.setCurrentPos(route.getStartPoint());
		routeStartTime = SimulationEngine.getInstance().getSimulationTime();
//		routesTime.append("< " + start.lat + " " + start.lon + " " + end.lat + " " + end.lon);
//		tracesTime.append("< " + start.lat + " " + start.lon + " " + end.lat + " " + end.lon + " " + (start.timestamp.getTime() - end.timestamp.getTime()) / 1000 + "\n");
	}
	
	public void printRouteData(String filename) {
		try {
			PrintWriter pw;
			/** TODO: to be done automatically **/
			pw = new PrintWriter(filename);
			pw.write(this.routesTime.toString());
			pw.close();
			this.routesTime = new StringBuffer();
//			pw = new PrintWriter("routes_" + getId() + ".txt");
//			pw.write(this.tracesTime.toString());
//			pw.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	public void initRoute() {
		try {
			if (isBeginNewRoute()) {
				resetRoute();
			}
			Iterator<GeoCarRoute> it = this.routes.iterator();

			/* Remove the routes that are empty. Stop for the first 
			 * one valid.
			 */
			while (it.hasNext()) {
				GeoCarRoute first = it.next();
				/* delete the routes that have no point in them */
				if (first.getIntersectionList().size() == 0)
					it.remove();
				else
					break;
			}

			/* If there is no route, car will be inactive */
			if (this.routes.size() == 0) {
				this.setActive(0);
//				printRouteData();
				logger.info("Car " + this.getId() + " has become inactive.");
				return;
			}
	
			this.initStartPosition();
			if (this.getCurrentPos() == null) {
				hasMoved.set(true);
				return;
			}
				
			if (!mobility.addCar(this)) {
				this.setCurrentPos(null);
				GeoCarRoute route = this.routes.remove(0);
				if (this.routes.size() > 0)
					this.routes.add(route);
			}
			hasMoved.set(true);
		} catch (RuntimeException e) {
			this.setCurrentPos(null);
			this.routes.remove(0);
			hasMoved.set(true);
		}
	}

	/**
	 * Remove the route; Reset the duration, speed and 
	 * delete the car from the correspondent cell of the graph
	 */
	public void resetRoute() {
		if (this.routes.size() != 0) {
			this.routes.remove(0);
		}
		seconds = 0;
		speed = 0.0;
		mobility.removeCar(this.getCurrentPos(), this.getId());
		this.setCurrentPos(null);
		finishMove();
	}

	/**
	 * Cancel the intermediate points between current position and 
	 * next position. Destroy the next position.
	 */
	public void finishMove() {
		nodesToMoveOver = null;
		nextPos = null;
		seconds++;
	}

	/**
	 * Test if a new route has to be started
	 */
	public boolean isBeginNewRoute() {
		return beginNewRoute;
	}

	/**
	 *  It is Set if a new route should be started or not
	 * @param beginNewRoute - True - a new route should be started
	 *                      - False - continue correct route
	 */
	public void setBeginNewRoute(boolean beginNewRoute) {
		this.beginNewRoute = beginNewRoute;
	}

	/**
	 * First initialization of the GeoCar Object
	 */
	public void start() {
		setBeginNewRoute(false);
		initRoute();
	}
}