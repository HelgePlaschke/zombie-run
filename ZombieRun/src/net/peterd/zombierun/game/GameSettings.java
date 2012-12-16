package net.peterd.zombierun.game;

import android.os.Bundle;
import net.peterd.zombierun.constants.BundleConstants;

public class GameSettings {
  
  private final Double zombiesPerSquareKilometer;
  private final Double zombieSpeedMetersPerSecond;
  private final boolean isMultiplayerGame;
  
  public GameSettings(Double zombiesPerSquareKilometer,
      Double zombieSpeedMetersPerSecond,
      boolean isMultiplayerGame) {
    this.zombiesPerSquareKilometer = zombiesPerSquareKilometer;
    this.zombieSpeedMetersPerSecond = zombieSpeedMetersPerSecond;
    this.isMultiplayerGame = isMultiplayerGame;
  }
  
  public void toBundle(Bundle bundle) {
    bundle.putDouble(BundleConstants.GAME_SETTING_ZOMBIE_DENSITY, getZombiesPerSquareKilometer());
    bundle.putDouble(BundleConstants.GAME_SETTING_ZOMBIE_SPEED, getZombieSpeedMetersPerSecond());
    bundle.putBoolean(BundleConstants.GAME_SETTING_IS_MULTIPLAYER_GAME, isMultiplayerGame);
  }
  
  public static GameSettings fromBundle(Bundle bundle) {
    if (bundle == null) {
      return null;
    }
    if (!bundle.containsKey(BundleConstants.GAME_SETTING_ZOMBIE_DENSITY) ||
        !bundle.containsKey(BundleConstants.GAME_SETTING_ZOMBIE_SPEED)) {
      return null;
    }
    return new GameSettings(
        bundle.getDouble(BundleConstants.GAME_SETTING_ZOMBIE_DENSITY),
        bundle.getDouble(BundleConstants.GAME_SETTING_ZOMBIE_SPEED),
        bundle.getBoolean(BundleConstants.GAME_SETTING_IS_MULTIPLAYER_GAME));
  }

  public Double getZombiesPerSquareKilometer() {
    return zombiesPerSquareKilometer;
  }

  public Double getZombieSpeedMetersPerSecond() {
    return zombieSpeedMetersPerSecond;
  }
  
  public boolean getIsMultiplayerGame() {
    return isMultiplayerGame;
  }
}
