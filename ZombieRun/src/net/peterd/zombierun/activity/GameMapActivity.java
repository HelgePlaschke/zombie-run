package net.peterd.zombierun.activity;

import java.util.ArrayList;
import java.util.Collection;

import net.peterd.zombierun.R;
import net.peterd.zombierun.constants.BundleConstants;
import net.peterd.zombierun.constants.Constants;
import net.peterd.zombierun.constants.Constants.GAME_MENU_OPTION;
import net.peterd.zombierun.game.GameEvent;
import net.peterd.zombierun.game.GameSettings;
import net.peterd.zombierun.overlay.MotoCliqSafeMyLocationOverlay;
import net.peterd.zombierun.service.GameEventBroadcaster;
import net.peterd.zombierun.service.GameService;
import net.peterd.zombierun.util.FloatingPointGeoPoint;
import net.peterd.zombierun.util.Log;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.maps.MapView;

/**
 * TODO: make the 'back' button go to the home screen (with a quit confirmation dialog), instead of
 * back to the last activity.
 *
 * @author Peter Dolan (peterjdolan@gmail.com)
 */
public class GameMapActivity extends BaseMapActivity {

  protected GameService service;
  protected final Collection<GAME_MENU_OPTION> menuOptions = new ArrayList<GAME_MENU_OPTION>();

  protected MapView mapView;
  protected Drawable myLocationDrawable;
  protected MotoCliqSafeMyLocationOverlay myLocationOverlay;
  protected GameSettings gameSettings;

  /**
   * Initialize the Map that activity takes place on.
   */
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);

    setContentView(R.layout.game);

    service = new GameService(this);

    mapView = (MapView) findViewById(R.id.mapview);
    MapView map = mapView;
    map.setFocusableInTouchMode(true);
    map.setClickable(true);
    map.setBuiltInZoomControls(true);

    map.getOverlays().clear();
    myLocationDrawable = getResources().getDrawable(R.drawable.mylocationdot);
    myLocationDrawable.setBounds(0,
        0,
        myLocationDrawable.getIntrinsicWidth(),
        myLocationDrawable.getIntrinsicHeight());
    myLocationOverlay =
        new MotoCliqSafeMyLocationOverlay(this, map, myLocationDrawable);
    service.getHardwareManager().registerLocationListener(myLocationOverlay);
    map.getOverlays().add(myLocationOverlay);

    map.setSatellite(true);

    Bundle intentExtras = getIntent().getExtras();
    if (intentExtras != null) {
      GameSettings settings = GameSettings.fromBundle(intentExtras);
      if (settings != null) {
        gameSettings = settings;
      }
    }

    if (state != null) {
      GameSettings settings = GameSettings.fromBundle(state);
      if (settings != null) {
        gameSettings = settings;
      }
    }

    // Restore from state after setting map view to satellite mode, so that the map's stored state
    // can override it if it's been set to map mode.
    if (state != null) {
      onRestoreInstanceState(state);
    }
  }

  @Override
  protected void onRestart() {
    super.onRestart();
    service.getEventHandler().broadcastEvent(GameEvent.GAME_RESUME);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      showQuitGameConfirmationDialogue();
      return true;
    }
    return false;
  }

  @Override
  protected void onResume() {
    super.onResume();
    service.getEventHandler().broadcastEvent(GameEvent.GAME_RESUME);
    Log.d("ZombieRun.GameMapActivity", "enabling mylocationoverlay");
    myLocationOverlay.enableMyLocation();
  }

  @Override
  protected void onPause() {
    super.onPause();
    service.getEventHandler().broadcastEvent(GameEvent.GAME_PAUSE);
    Log.d("ZombieRun.GameMapActivity", "disabling mylocationoverlay");
    myLocationOverlay.disableMyLocation();
  }

  @Override
  public void onRestoreInstanceState(Bundle state) {
    super.onRestoreInstanceState(state);
    Log.d("ZombieRun.GameMapActivity", "onRestoreInstanceState");
    if (mapView != null &&
        state != null &&
        state.containsKey(BundleConstants.MAP_MODE_IS_SATELLITE)) {
      mapView.setSatellite(state.getBoolean(BundleConstants.MAP_MODE_IS_SATELLITE));
    }
    service.onRestoreInstanceState(state);
    GameSettings settings = GameSettings.fromBundle(state);
    if (settings != null) {
      gameSettings = settings;
    }
  }

  @Override
  public void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    Log.d("ZombieRun.GameMapActivity", "onSaveInstanceState");
    state.putBoolean(BundleConstants.MAP_MODE_IS_SATELLITE, mapView.isSatellite());
    service.onSaveInstanceState(state);
    if (gameSettings != null) {
      gameSettings.toBundle(state);
    }
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    menu.clear();

    int menuIndex = 0;
    for (Constants.GAME_MENU_OPTION option : menuOptions) {
      if (option == GAME_MENU_OPTION.MAP_VIEW && !mapView.isSatellite()) {
        // Don't offer map view when we're showing map view.
        continue;
      }
      if (option == GAME_MENU_OPTION.SATELLITE_VIEW && mapView.isSatellite()) {
        // Don't offer satellite view when we're showing satellite view.
        continue;
      }
      menu.add(menuIndex, option.getValue(), Menu.NONE, option.getStringId());
      menuIndex++;
    }
    return true;
  }

  @Override
  public final boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    int itemId = item.getItemId();
    MapView mapView = this.mapView;
    final GameEventBroadcaster gameEventBroadcaster = service.getEventHandler();
    if (Constants.GAME_MENU_OPTION.PAUSE.getValue() == itemId) {
      new AlertDialog.Builder(this)
          .setMessage(getString(R.string.paused))
          .setCancelable(false)
          .setPositiveButton(getString(R.string.resume),
              new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                  gameEventBroadcaster.broadcastEvent(GameEvent.GAME_RESUME);
                }
              })
          .show();
      gameEventBroadcaster.broadcastEvent(GameEvent.GAME_PAUSE);
    } else if (Constants.GAME_MENU_OPTION.STOP.getValue() == itemId) {
      showQuitGameConfirmationDialogue();
    } else if (Constants.GAME_MENU_OPTION.MAP_VIEW.getValue() == itemId) {
      mapView.setSatellite(false);
    } else if (Constants.GAME_MENU_OPTION.SATELLITE_VIEW.getValue() == itemId) {
      mapView.setSatellite(true);
    } else if (Constants.GAME_MENU_OPTION.MY_LOCATION.getValue() == itemId) {
      FloatingPointGeoPoint point = service.getHardwareManager().getLastKnownLocation();
      if (point != null) {
        mapView.getController().animateTo(point.getGeoPoint());
      }
    }
    return false;
  }

  private void showQuitGameConfirmationDialogue() {
    final GameEventBroadcaster gameEventBroadcaster = service.getEventHandler();
    // Showing a quit game confirmation dialogue.
    final Intent quitGameIntent = new Intent(this, WinOrLoseGame.class);
    quitGameIntent.putExtra(BundleConstants.GAME_WON, false);
    new AlertDialog.Builder(this)
        .setMessage(R.string.confirm_menu_stop)
        .setPositiveButton(getString(R.string.quit_confirm),
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                gameEventBroadcaster.broadcastEvent(GameEvent.GAME_QUIT);
                startActivity(quitGameIntent);
              }
            })
        .setNegativeButton(getString(R.string.quit_cancel),
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                // Nothing, just go back to the game.
              }
            })
        .show();
  }
}
