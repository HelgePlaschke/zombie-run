package net.peterd.zombierun.service;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import net.peterd.zombierun.constants.Constants;
import net.peterd.zombierun.entity.Destination;
import net.peterd.zombierun.entity.Player;
import net.peterd.zombierun.entity.Zombie;
import net.peterd.zombierun.entity.ZombiePopulator;
import net.peterd.zombierun.game.GameEvent;
import net.peterd.zombierun.game.GameSettings;
import net.peterd.zombierun.game.GameState;
import net.peterd.zombierun.io.NetworkDataFetcher;
import net.peterd.zombierun.service.remote.GameOwnerStateSynchronizer;
import net.peterd.zombierun.service.remote.GameServerBridge;
import net.peterd.zombierun.service.remote.ParticipantStateSynchronizer;
import net.peterd.zombierun.service.remote.RemoteGameStateSynchronizer;
import net.peterd.zombierun.util.FloatingPointGeoPoint;

/**
 * Responsible for maintaining game state.  Handles initializing the game state, updating it as the
 * game progresses, and synchronizing game state with the game server.
 *
 * @author Peter Dolan (peterjdolan@gmail.com)
 */
public class GameService {
  
  private final HardwareManager hardwareManager;
  private final GameEventHandler eventHandler = new GameEventHandler();
  private final GameServerBridge gameServerBridge = new GameServerBridge(new NetworkDataFetcher());
  private GameServerBridge.ServerData gameServerData;

  private GameState state;
  private LocalGameStateInvalidator invalidator;
  private RemoteGameStateSynchronizer remoteSynchronizer;
  
  public GameService(Activity activity) {
    // The GameService must not hang onto the Activity it is given in the
    // constructor.
    
    // The HardwareManager does not hold onto the activity.
    hardwareManager = new HardwareManager(activity);
    hardwareManager.initializeHardware();
    eventHandler.addListener(hardwareManager);
  }
  
  public void shutDown() {
    hardwareManager.deregisterManager();
  }
  
  public HardwareManager getHardwareManager() {
    return hardwareManager;
  }
  
  public GameState getGameState() {
    return state;
  }
  
  public GameEventHandler getEventHandler() {
    return eventHandler;
  }

  public void joinMultiPlayerGame(int gameId) {
    // XXX: handle IO errors.
    // XXX: handle game not found.
    // TODO: run this joining action in a non-UI thread.
    gameServerData = gameServerBridge.join(gameId);
    state = new GameState();
    remoteSynchronizer =
        new ParticipantStateSynchronizer(
            gameServerData,
            state,
            gameServerBridge,
            Constants.multiPlayerGameSynchronizationIntervalMs,
            eventHandler);
    remoteSynchronizer.start();
    eventHandler.addListener(remoteSynchronizer);
    
    // TODO: wait for first successful game state fetch, then populate the local variables like the
    // destination and initial zombie locations.
    //
    // Actually, this should work reasonably well if it just goes off and does its thing, fetching
    // everything.  Just need to show an indicator that the game is not yet started.
  }

  public void createMultiPlayerGame(FloatingPointGeoPoint startingLocation,
      Destination destination,
      GameSettings settings) {
    createGame(startingLocation, destination, settings);
    // XXX: handle IO errors somehow.
    // TODO: run this creating action in a non-UI thread.
    gameServerData = gameServerBridge.create();
    remoteSynchronizer =
        new GameOwnerStateSynchronizer(
            gameServerData,
            state,
            gameServerBridge,
            Constants.multiPlayerGameSynchronizationIntervalMs,
            eventHandler);
    remoteSynchronizer.start();
    eventHandler.addListener(remoteSynchronizer);
  }

  public void createSinglePlayerGame(FloatingPointGeoPoint startingLocation,
      Destination destination,
      GameSettings settings) {
    createGame(startingLocation, destination, settings);
  }
  
  private void createGame(FloatingPointGeoPoint startingLocation,
      Destination destination,
      GameSettings gameSettings) {
    if (startingLocation == null) {
      throw new IllegalArgumentException("startingLocation cannot be null.");
    }
    if (destination == null) {
      throw new IllegalArgumentException("destination cannot be null.");
    }
    if (gameSettings == null) {
      throw new IllegalArgumentException("gameSettings cannot be null.");
    }
    if (hardwareManager == null) {
      throw new IllegalArgumentException("hardwareManager cannot be null.");
    }
    
    state = new GameState(destination);
    GameState state = this.state;
    GameEventHandler handler = this.eventHandler;

    ZombiePopulator populator =
        new ZombiePopulator(state,
            startingLocation,
            destination.getLocation(),
            gameSettings.getZombieSpeedMetersPerSecond(),
            (int) Math.round(gameSettings.getZombiesPerSquareKilometer()),
            handler);
    populator.populate();
    
    int nextPlayerId = state.getPlayers().size();
    Player thisDevicePlayer = new Player(state.getDestination(),
        nextPlayerId,
        null,
        handler);
    state.getPlayers().add(thisDevicePlayer);
    state.setThisDevicePlayer(thisDevicePlayer);
    hardwareManager.registerLocationListener(thisDevicePlayer);
    
    registerAllEntitiesAsGameEventListeners(state, handler);
    handler.addListener(hardwareManager);
    
    invalidator = new LocalGameStateInvalidator(state, handler);
    handler.addListener(invalidator);
    invalidator.run();
  }

  public void onRestoreInstanceState(Bundle savedInstanceState) {
    if (savedInstanceState != null && state != null) {
      state.fromBundle(savedInstanceState, eventHandler);
    }
  }

  public void onSaveInstanceState(Bundle savedInstanceState) {
    if (state != null) {
      state.toBundle(savedInstanceState);
    }
  }

  private void registerAllEntitiesAsGameEventListeners(GameState state,
      GameEventHandler gameEventHandler) {
    for (Player player : state.getPlayers()) {
      gameEventHandler.addListener(player);
    }
    for (Zombie zombie : state.getZombies()) {
      gameEventHandler.addListener(zombie);
    }
  }
  
  private static class LocalGameStateInvalidator implements GameEventListener, Runnable {
    
    private final GameState state;
    private final Handler handler = new Handler();
    private final GameEventBroadcaster broadcaster;
    private boolean gameActive = true;
    
    public LocalGameStateInvalidator(GameState state, GameEventBroadcaster broadcaster) {
      this.state = state;
      this.broadcaster = broadcaster;
    }

    public void receiveEvent(GameEvent event) {
      if (event == GameEvent.GAME_RESUME ||
          event == GameEvent.GAME_START) {
        gameActive = true;
      } else if (event == GameEvent.GAME_LOSE ||
          event == GameEvent.GAME_PAUSE ||
          event == GameEvent.GAME_QUIT ||
          event == GameEvent.GAME_WIN) {
        gameActive = false;
      }
    }
    
    public void run() {
      if (gameActive) {
        state.AdvanceZombies(Constants.gameUpdateDelayMs);
        broadcaster.broadcastEvent(GameEvent.UPDATED_ZOMBIE_LOCATIONS);
      }
      handler.postDelayed(this, Constants.gameUpdateDelayMs);
    }
  }
}
