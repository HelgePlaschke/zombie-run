package net.peterd.zombierun.service.remote;

import net.peterd.zombierun.game.GameEvent;
import net.peterd.zombierun.game.GameState;
import net.peterd.zombierun.service.GameEventBroadcaster;
import net.peterd.zombierun.service.GameEventListener;

public abstract class RemoteGameStateSynchronizer implements GameEventListener {

  protected final int gameId;
  protected final String secretKey;
  protected final int playerId;
  private final int synchronizationIntervalMs;
  protected final GameState state;
  protected final GameServerBridge bridge;
  protected final GameEventBroadcaster eventBroadcaster;

  private InvalidatingThread invalidatingThread;
  
  public RemoteGameStateSynchronizer(GameServerBridge.ServerData serverData,
      GameState state,
      GameServerBridge bridge,
      int synchronizationIntervalMs,
      GameEventBroadcaster eventBroadcaster) {
    this.gameId = serverData.gameId;
    this.secretKey = serverData.secret;
    this.playerId = serverData.playerId;
    this.synchronizationIntervalMs = synchronizationIntervalMs;
    this.state = state;
    this.bridge = bridge;
    this.eventBroadcaster = eventBroadcaster;
  }
  
  public void receiveEvent(GameEvent event) {
    if (event == GameEvent.GAME_LOSE ||
        event == GameEvent.GAME_PAUSE ||
        event == GameEvent.GAME_QUIT ||
        event == GameEvent.GAME_WIN) {
      stop();
    } else if (event == GameEvent.GAME_RESUME) {
      // Not also handling GAME_START, as the synchronizer should be started by the service when
      // the game starts.
      start();
    }
  }
  
  public void start() {
    invalidatingThread = new InvalidatingThread(this, synchronizationIntervalMs);
    invalidatingThread.start();
  }
  
  public void stop() {
    invalidatingThread.signalStop();
  }
  
  protected abstract void invalidate();
  
  private class InvalidatingThread extends Thread {
    private final RemoteGameStateSynchronizer synchronizer;
    private final int invalidationIntervalMs;
    private volatile boolean running = true;
    
    public InvalidatingThread(RemoteGameStateSynchronizer synchronizer,
        int invalidationIntervalMs) {
      this.synchronizer = synchronizer;
      this.invalidationIntervalMs = invalidationIntervalMs;
    }
    
    public void signalStop() {
      running = false;
    }
    
    @Override
    public void run() {
      while (running) {
        synchronizer.invalidate();
        try {
          sleep(invalidationIntervalMs);
        } catch (InterruptedException e) {
          return;
        }
      }
    }
  }
}
