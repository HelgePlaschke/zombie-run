// Copyright 2009 Peter Dolan, all rights reserved.

package net.peterd.zombierun.activity;

import net.peterd.zombierun.util.Log;
import android.content.res.Configuration;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;
import com.google.android.maps.MapActivity;

public class BaseMapActivity extends MapActivity {

  protected GoogleAnalyticsTracker tracker;
  
  @Override
  public void onStart() {
    super.onStart();
    BaseActivity.logToAnalytics(this);
  }
  
  @Override
  protected boolean isRouteDisplayed() {
    return false;
  }
  

  @Override
  public void onConfigurationChanged(Configuration configuration) {
    super.onConfigurationChanged(configuration);

    // Do nothing.  We don't do orientation changes in the game screen.
    Log.d("ZombieRun.GameMapActivity", "onConfigurationChanged handled.");
  }
}
