package net.peterd.zombierun.entity;

import android.os.Bundle;
import net.peterd.zombierun.NotImplementedException;
import net.peterd.zombierun.util.FloatingPointGeoPoint;

public class Destination {

  private final FloatingPointGeoPoint location;
  
  public static final String DESTINATION_BUNDLE_KEY = "net.peterd.zombierun.entity.Destination";
  
  public Destination(FloatingPointGeoPoint location) {
    this.location = location;
  }
  
  public FloatingPointGeoPoint getLocation() {
    return location;
  }
  
  public String toString() {
    // TODO: remove this method once we're sure it's not being used.
    throw new NotImplementedException();
  }
  
  public static Destination fromString(String string) {
    FloatingPointGeoPoint fpgp =
        FloatingPointGeoPoint.fromString(string);
    return fpgp == null ? null : new Destination(fpgp);
  }
  
  public static Destination fromBundle(Bundle bundle) {
    if (!bundle.containsKey(DESTINATION_BUNDLE_KEY)) {
      return null;
    }
    FloatingPointGeoPoint fpgp =
        FloatingPointGeoPoint.fromString(bundle.getString(DESTINATION_BUNDLE_KEY));
    return fpgp == null ? null : new Destination(fpgp);
  }

  public void toBundle(Bundle bundle) {
    bundle.putString(DESTINATION_BUNDLE_KEY, location.toString());
  }
}
