package net.peterd.zombierun.overlay;

import java.util.ArrayList;
import java.util.List;

import net.peterd.zombierun.entity.Zombie;
import net.peterd.zombierun.game.GameEvent;
import net.peterd.zombierun.service.GameEventListener;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;

public class ZombieOverlay extends Overlay implements GameEventListener {

  private final List<Zombie> zombies;
  private final MapView mapView;
  private final Drawable zombieMeanderingDrawable;
  private final Drawable zombieNoticingPlayerDrawable;
  private ItemizedOverlay<ZombieOverlayItem> internalOverlay;

  public ZombieOverlay(List<Zombie> zombies, MapView mapView, Drawable zombieMeanderingDrawable,
      Drawable zombieNoticingPlayeDrawable) {
    super();
    this.zombies = zombies;
    this.mapView = mapView;
    this.zombieMeanderingDrawable = zombieMeanderingDrawable;
    this.zombieNoticingPlayerDrawable = zombieNoticingPlayeDrawable;
  }

  @Override
  public void draw(Canvas canvas, MapView view, boolean shadow) {
    super.draw(canvas, view, shadow);
    if (internalOverlay != null) {
      internalOverlay.draw(canvas, view, shadow);
    }
  }

  public void receiveEvent(GameEvent event) {
    if (event == GameEvent.UPDATED_ZOMBIE_LOCATIONS) {
      Handler handler = new Handler();
      handler.post(new Runnable() {
            public void run() {
              initInternalOverlay();
            }
          });
    }
  }

  private void initInternalOverlay() {
    List<Zombie> visibleZombies = new ArrayList<Zombie>(zombies.size());
    MapView mapView = this.mapView;
    GeoPoint mapCenter = mapView.getMapCenter();
    int latSpan = mapView.getLatitudeSpan();
    int lonSpan = mapView.getLongitudeSpan();

    int maxLat = mapCenter.getLatitudeE6() + latSpan / 2;
    int minLat = mapCenter.getLatitudeE6() - latSpan / 2;
    int maxLon = mapCenter.getLongitudeE6() + lonSpan / 2;
    int minLon = mapCenter.getLongitudeE6() - lonSpan / 2;

    List<Zombie> zombies = this.zombies;
    for (int i = 0; i < zombies.size(); ++i) {
      Zombie zombie = zombies.get(i);
      if (zombie.getLatitudeE6() < maxLat &&
          zombie.getLatitudeE6() > minLat &&
          zombie.getLongitudeE6() < maxLon &&
          zombie.getLongitudeE6() > minLon) {
        visibleZombies.add(zombie);
      }
    }

    if (visibleZombies.size() > 0) {
      internalOverlay = new ItemizedZombieOverlay(visibleZombies);
    } else {
      internalOverlay = null;
    }
  }

  private class ItemizedZombieOverlay extends ItemizedOverlay<ZombieOverlayItem> {

    private final List<Zombie> zombies;

    public ItemizedZombieOverlay(List<Zombie> zombies) {
      super(zombieMeanderingDrawable);
      this.zombies = zombies;
      boundCenterBottom(zombieMeanderingDrawable);
      boundCenterBottom(zombieNoticingPlayerDrawable);
      populate();
    }

    @Override
    protected ZombieOverlayItem createItem(int i) {
      return new ZombieOverlayItem(zombies.get(i));
    }

    @Override
    public int size() {
      return zombies.size();
    }
  }

  private class ZombieOverlayItem extends OverlayItem {
    private final Zombie zombie;

    public ZombieOverlayItem(Zombie zombie) {
      super(new GeoPoint(zombie.getLatitudeE6(), zombie.getLongitudeE6()), "", "");
      this.zombie = zombie;
    }

    @Override
    public Drawable getMarker(int stateBitset) {
      return zombie.isNoticingPlayer() ? zombieNoticingPlayerDrawable : zombieMeanderingDrawable;
    }
  }
}
