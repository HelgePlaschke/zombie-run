package net.peterd.zombierun.activity;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import net.peterd.zombierun.NotImplementedException;
import net.peterd.zombierun.R;
import net.peterd.zombierun.constants.BundleConstants;
import net.peterd.zombierun.constants.Constants;
import net.peterd.zombierun.constants.Constants.GAME_MENU_OPTION;
import net.peterd.zombierun.entity.Destination;
import net.peterd.zombierun.entity.Player;
import net.peterd.zombierun.game.GameEvent;
import net.peterd.zombierun.overlay.DestinationOverlay;
import net.peterd.zombierun.overlay.ZombieOverlay;
import net.peterd.zombierun.service.GameEventListener;
import net.peterd.zombierun.service.GameService;
import net.peterd.zombierun.util.FloatingPointGeoPoint;
import net.peterd.zombierun.util.Log;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import com.google.android.maps.MapView;

public abstract class Game extends GameMapActivity implements GameEventListener {

  protected Drawable zombieMeanderingDrawable;
  protected Drawable zombieNoticingPlayerDrawable;
  protected Drawable destinationDrawable;

  public Game() {
    menuOptions.addAll(Arrays.asList(GAME_MENU_OPTION.values()));
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    Log.d("ZombieRun.Game", "onCreate");
    if (state != null) {
      Log.d("ZombieRun.Game", "onCreate with bundle that has " + state.size() +
          " key + value pairs.");
    } else {
      Log.d("ZombieRun.Game", "onCreate with null bundle.");
    }

    initializeDrawables();

    // TODO: Initialize a connection to the game service, which should have been created by the
    // entrypoint to this Activity.  A singleplayer-start-game entrypoint, multiplayer-game-master
    // entrypoint, and a multiplayer-join-game entrypoint.
    //
    // This is where we should get the zombie locations and the game destination.
    Bundle intentBundle = getIntent().getExtras();
    Destination destination = Destination.fromBundle(intentBundle);
    if (destination == null) {
      throw new RuntimeException("Cannot enter this activity with a null destination in the " +
          "bundle.  Bundle: '" + intentBundle.toString() + "'.");
    }

    if (state != null) {
      onRestoreInstanceState(state);
    }

    createGame(service, destination);

    // Game State is initialized in Service#createSinglePlayerGame
    initializeDestinationOverlay(mapView);
    initializeZombieOverlay(mapView);
  }

  protected boolean showAds() {
    return false;
  }

  @Override
  protected void onResume() {
    super.onResume();
    service.getEventHandler().addListener(this);
  }

  @Override
  protected void onPause() {
    super.onPause();
    service.getEventHandler().removeListener(this);
  }

  protected abstract void createGame(GameService service, Destination destination);

  private void initializeDrawables() {
    zombieMeanderingDrawable = getResources().getDrawable(R.drawable.zombie1body);
    zombieMeanderingDrawable.setBounds(0, 0,
        zombieMeanderingDrawable.getIntrinsicWidth(),
        zombieMeanderingDrawable.getIntrinsicHeight());

    zombieNoticingPlayerDrawable = getResources().getDrawable(R.drawable.zombie1bodynoticingplayer);
    zombieNoticingPlayerDrawable.setBounds(0, 0,
        zombieNoticingPlayerDrawable.getIntrinsicWidth(),
        zombieNoticingPlayerDrawable.getIntrinsicHeight());

    destinationDrawable = getResources().getDrawable(R.drawable.flag);
    destinationDrawable.setBounds(0, 0,
        destinationDrawable.getIntrinsicHeight(),
        destinationDrawable.getIntrinsicHeight());
  }

  public void initializeZombieOverlay(MapView map) {
    ZombieOverlay overlay =
        new ZombieOverlay(
            service.getGameState().getZombies(),
            map,
            zombieMeanderingDrawable,
            zombieNoticingPlayerDrawable);
    service.getEventHandler().addListener(overlay);
    map.getOverlays().add(overlay);
    map.postInvalidate();
  }

  public void initializePlayersOverlay(MapView map) {
    throw new NotImplementedException();
  }

  public void initializeDestinationOverlay(MapView map) {
    AtomicReference<Destination> destinationReference = new AtomicReference<Destination>();
    destinationReference.set(service.getGameState().getDestination());
    map.getOverlays().add(new DestinationOverlay(destinationReference, destinationDrawable));
  }

  protected void centerOnPlayer(Player player) {
    FloatingPointGeoPoint playerLocation = player.getLocation();
    if (playerLocation != null) {
      MapView mapView = this.mapView;
      mapView.getController().animateTo(playerLocation.getGeoPoint());
      // If the map is zoomed too far out to be really reasonable (i.e. zoomed to the entire globe),
      // then zoom in a bit so that people can see themselves.
      if (mapView.getZoomLevel() < 5) {
        mapView.getController().setZoom(Constants.defaultMapZoomLevel);
      }
      mapView.displayZoomControls(true);
    }
  }

  private void onQuitGame() {
    onLoseGame();
  }

  private void onLoseGame() {
    Intent intent = new Intent(this, WinOrLoseGame.class);
    intent.putExtra(BundleConstants.GAME_WON, false);
    startActivity(intent);
  }

  private void onWinGame() {
    Intent intent = new Intent(this, WinOrLoseGame.class);
    intent.putExtra(BundleConstants.GAME_WON, true);
    startActivity(intent);
  }

  private void onZombieNearPlayer(Player player) {
    mapView.getController().animateTo(player.getLocation().getGeoPoint());
    if (mapView.getZoomLevel() < 19) {
      mapView.getController().setZoom(19);
    }
  }

  public void receiveEvent(GameEvent event) {
    if (event == GameEvent.GAME_LOSE) {
      onLoseGame();
    } else if (event == GameEvent.GAME_QUIT) {
      onQuitGame();
    } else if (event == GameEvent.GAME_WIN) {
      onWinGame();
    } else if (event == GameEvent.ZOMBIE_NEAR_PLAYER) {
      onZombieNearPlayer(service.getGameState().getThisDevicePlayer());
    } else if (event == GameEvent.ZOMBIE_CATCH_PLAYER) {
      service.getEventHandler().broadcastEvent(GameEvent.GAME_LOSE);
    } else if (event == GameEvent.PLAYER_REACHES_DESTINATION) {
      service.getEventHandler().broadcastEvent(GameEvent.GAME_WIN);
    }
  }
}
