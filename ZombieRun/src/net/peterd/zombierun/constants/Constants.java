package net.peterd.zombierun.constants;

import net.peterd.zombierun.R;

public class Constants {
  public static final float minDeviceAccuracyMeters = 120.0f;
  public static final int defaultMapZoomLevel = 16;
  public static final int gameUpdateDelayMs = 500;
  public static final float gameTargetDistanceMeters = 250;
  public static final long radiusOfEarthMeters = 6378100;
  public static final int multiPlayerGameSynchronizationIntervalMs = 5000;

  // Const Zombie settings
  public static final float zombieSpeedPercentageDeviationFromMean = 0.2f;
  public static final int maxZombieCount = 150;
  public static final float minZombieDistanceFromStartingPointMeters = 100f;
  public static final float maxZombieClusterSizeMeters = 10f;
  public static final int maxZombieClusterSize = 3;
  public static final float zombieNoticePlayerDistanceMeters = 200f;
  public static final int onZombieNoticePlayerVibrationTimeMs = 500;
  public static final float zombieNearPlayerDistanceMeters = 50f;
  public static final int onZombieNearPlayerVibrationTimeMs = 250;
  public static final float zombieCatchPlayerDistanceMeters = 2f;
  public static final int onGameEndVibrationTimeMs = 2000;
  
  public static final float reachDestinationTestDistanceMeters = 30f;
    
  public enum GAME_MENU_OPTION {
    PAUSE(0, R.string.menu_pause),
    STOP(1, R.string.menu_stop),
    MY_LOCATION(2, R.string.menu_my_location),
    MAP_VIEW(3, R.string.menu_map_view),
    SATELLITE_VIEW(4, R.string.menu_satellite_view);
    
    private int value;
    private int stringId;
    
    private GAME_MENU_OPTION(int value, int stringId) {
      this.value = value;
      this.stringId = stringId;
    }
    
    public int getValue() {
      return value;
    }
    
    public int getStringId() {
      return stringId;
    }
    
    public static GAME_MENU_OPTION valueOf(int optionId) {
      for (GAME_MENU_OPTION option : values()) {
        if (option.getValue() == optionId) {
          return option;
        }
      }
      return null;
    }
  }
}
