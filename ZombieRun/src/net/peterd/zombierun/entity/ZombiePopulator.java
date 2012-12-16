package net.peterd.zombierun.entity;

import java.util.List;

import net.peterd.zombierun.util.Log;

import net.peterd.zombierun.constants.Constants;
import net.peterd.zombierun.game.GameState;
import net.peterd.zombierun.service.GameEventBroadcaster;
import net.peterd.zombierun.util.FloatingPointGeoPoint;
import net.peterd.zombierun.util.GeoPointUtil;

public class ZombiePopulator {

  private final GameState gameState;
  private final FloatingPointGeoPoint startingLocation;
  private final FloatingPointGeoPoint destination;
  private final GameEventBroadcaster gameEventBroadcaster;
  private final double averageZombieSpeedMetersPerSecond;
  private final double zombiesPerSquareKilometer;
  
  public ZombiePopulator(GameState gameState,
      FloatingPointGeoPoint startingLocation,
      FloatingPointGeoPoint destination,
      double averageZombieSpeedMetersPerSecond,
      double zombiesPerSquareKilometer,
      GameEventBroadcaster gameEventBroadcaster) {
    this.gameState = gameState;
    this.startingLocation = startingLocation;
    this.destination = destination;
    this.averageZombieSpeedMetersPerSecond = averageZombieSpeedMetersPerSecond;
    this.zombiesPerSquareKilometer = zombiesPerSquareKilometer;
    this.gameEventBroadcaster = gameEventBroadcaster;
  }
  

  private double maxRadiusMeters;
  public void populate() {
    int zombieId = 0;
    List<Zombie> zombies = gameState.getZombies();
    List<Player> players = gameState.getPlayers();
    GameEventBroadcaster broadcaster = gameEventBroadcaster;
    double averageZombieSpeed = averageZombieSpeedMetersPerSecond;
    
    maxRadiusMeters = GeoPointUtil.distanceMeters(startingLocation.getLatitude(), 
        startingLocation.getLongitude(), 
        destination.getLatitude(),
        destination.getLongitude()) * 2;
    double areaOfPopulationSquareKilometers = Math.PI * Math.pow(maxRadiusMeters / 1000, 2);
    int zombieCount =
        Math.min(
            (int) Math.round(areaOfPopulationSquareKilometers * zombiesPerSquareKilometer),
            Constants.maxZombieCount);
    
    while (true) {
      int clusterSize = (int) Math.round(Math.random() * Constants.maxZombieClusterSize) + 1;
      FloatingPointGeoPoint clusterCentroid = getNewZombieClusterCentroid();
      for (int i = 0; i < clusterSize; i++) {
        if (zombies.size() >= zombieCount) {
          return;
        }
        FloatingPointGeoPoint zombieLocation =
            GeoPointUtil.getGeoPointNear(clusterCentroid.getLatitude(),
                clusterCentroid.getLongitude(),
                Math.random() * Constants.maxZombieClusterSizeMeters);
        
        double zombieSpeed =
            averageZombieSpeed +
            ((Math.random() - 0.5) *
                averageZombieSpeed *
                Constants.zombieSpeedPercentageDeviationFromMean);
        
        Log.d("ZombieRun.ZombiePopulator", "Zombie speed: " + zombieSpeed + "m/s.");
        Zombie zombie = new Zombie(zombieId,
            zombieLocation,
            players,
            zombieSpeed,
            broadcaster);
        zombies.add(zombie);
        zombieId++;
      }
    }
  }
  
  private FloatingPointGeoPoint getPlayerDestinationMidpoint() {
    double distanceBetween =
        GeoPointUtil.distanceMeters(startingLocation.getLatitude(),
            startingLocation.getLongitude(),
            destination.getLatitude(),
            destination.getLongitude());
    return GeoPointUtil.geoPointTowardsTarget(
        startingLocation.getLatitude(),
        startingLocation.getLongitude(),
        destination.getLatitude(),
        destination.getLongitude(),
        distanceBetween / 2);
  }
  
  private FloatingPointGeoPoint getNewZombieClusterCentroid() {
    FloatingPointGeoPoint startingLocation = this.startingLocation;
    FloatingPointGeoPoint midPoint = getPlayerDestinationMidpoint();
    FloatingPointGeoPoint candidatePoint =
        GeoPointUtil.getGeoPointNear(
            midPoint.getLatitude(),
            midPoint.getLongitude(),
            Math.random() * maxRadiusMeters);
    double distanceFromStartingPoint =
        GeoPointUtil.distanceMeters(
            startingLocation.getLatitude(),
            startingLocation.getLongitude(),
            candidatePoint.getLatitude(),
            candidatePoint.getLongitude());
    if (distanceFromStartingPoint < Constants.minZombieDistanceFromStartingPointMeters) {
      return GeoPointUtil.geoPointTowardsTarget(
          startingLocation.getLatitude(),
          startingLocation.getLongitude(),
          candidatePoint.getLatitude(),
          candidatePoint.getLongitude(),
          Constants.minZombieDistanceFromStartingPointMeters);
    } else {
      return candidatePoint;
    }
  }
}
