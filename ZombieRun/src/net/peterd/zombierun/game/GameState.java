package net.peterd.zombierun.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import android.os.Bundle;
import net.peterd.zombierun.util.Log;

import net.peterd.zombierun.entity.Destination;
import net.peterd.zombierun.entity.Player;
import net.peterd.zombierun.entity.Zombie;
import net.peterd.zombierun.service.GameEventBroadcaster;

public class GameState {
  
  private final List<Zombie> zombies = new ArrayList<Zombie>();
  private final List<Player> players = new ArrayList<Player>();
  private int indexOfThisDevicePlayer;
  private Destination destination;

  private static final String zombieHordeBundleKey =
      "net.peterd.zombierun.service.GameState.ZombieHorde";

  public GameState() {
    // Nothing to see here.
  }
  
  public GameState(Destination destination) {
    setDestination(destination);
  }
  
  public Destination getDestination() {
    return destination;
  }
  
  public void setDestination(Destination destination) {
    this.destination = destination;
  }
  
  public List<Zombie> getZombies() {
    return zombies;
  }
  
  public List<Player> getPlayers() {
    return players;
  }
  
  public void setThisDevicePlayer(Player player) {
    indexOfThisDevicePlayer = players.indexOf(player);
    assert indexOfThisDevicePlayer > 0;
  }
  
  public Player getThisDevicePlayer() {
    return players.get(indexOfThisDevicePlayer);
  }
  
  public void toBundle(Bundle state) {
    destination.toBundle(state);
    state.putString(zombieHordeBundleKey, Zombie.ZombieListSerializer.toString(zombies));
  }
  
  public void fromBundle(Bundle state, GameEventBroadcaster gameEventBroadcaster) {
    destination = Destination.fromBundle(state);

    List<Zombie> zombies = this.zombies;
    zombies.clear();
    zombies.addAll(
        Zombie.ZombieListSerializer.fromString(
            state.getString(zombieHordeBundleKey), players, gameEventBroadcaster));
  }
  
  public void AdvanceZombies(long deltaTimeMs) {
    Log.d("ZombieRun.GameState", "Advancing Zombies.");
    List<Zombie> zombies = getZombies();
    // don't allocate a list iterator object.
    for (int i = 0; i < zombies.size(); ++i) {
      zombies.get(i).advance(deltaTimeMs, TimeUnit.MILLISECONDS);
    }
  }
}
