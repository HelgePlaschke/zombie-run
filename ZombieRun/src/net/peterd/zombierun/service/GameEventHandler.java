package net.peterd.zombierun.service;

import java.util.WeakHashMap;

import net.peterd.zombierun.game.GameEvent;
import net.peterd.zombierun.util.Log;

public class GameEventHandler implements GameEventBroadcaster {

  // We use a weak hash map to ensure that if an object is added to this as a
  // listener, but never removes itself, we won't keep it from being
  // garbage collected.
  private final WeakHashMap<GameEventListener, Boolean> listeners =
      new WeakHashMap<GameEventListener, Boolean>();

  public synchronized boolean addListener(GameEventListener listener) {
    Log.d("ZombieRun.GameEventHandler", "Adding GameEventListener " + listener.toString());
    if (listeners.put(listener, true) == null) {
      return true;
    } else {
      return false;
    }
  }

  public synchronized boolean removeListener(GameEventListener listener) {
    Log.d("ZombieRun.GameEventHandler", "Removing GameEventListener " + listener.toString());
    return listeners.remove(listener);
  }

  public synchronized void clearListeners() {
    Log.d("ZombieRun.GameEventHandler", "Clearing GameEventListeners.");
    listeners.clear();
  }

  public synchronized void broadcastEvent(GameEvent event) {
    int severity = android.util.Log.INFO;
    if (event == GameEvent.UPDATED_PLAYER_LOCATIONS ||
        event == GameEvent.UPDATED_ZOMBIE_LOCATIONS) {
      severity = android.util.Log.DEBUG;
    }

    if (Log.loggingEnabled()) {
      Log.println(severity, "ZombieRun.GameEventHandler", "Broadcasting event " + event.name());
    }

    for (GameEventListener listener : listeners.keySet()) {
      if (listener != null) {
        listener.receiveEvent(event);
      }
    }
  }
}
