package net.peterd.zombierun.overlay;

import java.util.List;

import net.peterd.zombierun.util.GeoPointUtil;
import net.peterd.zombierun.util.Log;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;

public class MotoCliqSafeMyLocationOverlay extends MyLocationOverlay implements LocationListener {

  private ItemizedOverlay<OverlayItem> backup_delegate;
  private boolean drawWithBackupDelegate = false;
  private final MapView map;
  private final Drawable myLocationDot;

	public MotoCliqSafeMyLocationOverlay(Context context,
	    MapView mapView,
	    Drawable myLocationDrawable) {
	  super(context, mapView);
		map = mapView;
		myLocationDot = myLocationDrawable;
		enableMyLocation();
	}

	@Override
	public void drawMyLocation(Canvas canvas,
	    MapView mapView,
	    Location lastFix,
	    GeoPoint myLocation,
	    long when) {
		try {
			super.drawMyLocation(canvas, mapView, lastFix, myLocation, when);
		} catch (Exception e) {
		  Log.d("ZombieRun.MotoCliqSafeMyLocationOverlay", "Caught exception " + e.getMessage());
		  drawWithBackupDelegate = true;
		}
  }

	private void initializeBackupDelegate(GeoPoint location) {
    if (!drawWithBackupDelegate) {
      return;
    }

    Log.d("ZobmieRun.MotoCliqSafeMyLocationOverlay", "Initializing backup delegate.");

	  if (location == null) {
	    Log.d("ZombieRun.MotoCliqSafeMyLocationOverlay",
	        "Current location null, cannot draw my location.");
	  }

	  backup_delegate = new MyLocationItemizedOverlay(location, myLocationDot);
	  if (drawWithBackupDelegate) {
	    List<Overlay> overlays = map.getOverlays();
	    for (int i = 0; i < overlays.size(); ++i) {
	      if (overlays.get(i) instanceof MyLocationItemizedOverlay) {
	        overlays.remove(i);
	        break;
	      }
	    }
	    overlays.add(backup_delegate);
	    map.postInvalidate();
	  }
	}

  @Override
  public synchronized void onLocationChanged(Location location) {
    super.onLocationChanged(location);

    Log.d("ZobmieRun.MotoCliqSafeMyLocationOverlay", "Received updated location.");
    initializeBackupDelegate(GeoPointUtil.fromLocation(location));
  }

  private class MyLocationItemizedOverlay extends ItemizedOverlay<OverlayItem> {

    private final OverlayItem item;

    public MyLocationItemizedOverlay(GeoPoint location, Drawable marker) {
      super(boundCenter(marker));
      item = new OverlayItem(location, "", "");
      populate();
    }

    @Override
    protected OverlayItem createItem(int i) {
      return item;
    }

    @Override
    public int size() {
      return 1;
    }
  }
}
