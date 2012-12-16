// Copyright 2009 Peter Dolan, all rights reserved.

package net.peterd.zombierun.activity;

import net.peterd.zombierun.util.Log;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

public class BaseActivity extends Activity {
  
  private static GoogleAnalyticsTracker tracker;

  @Override
  public void onStart() {
    super.onStart();
    logToAnalytics(this);
  }
  

  @Override
  public void onConfigurationChanged(Configuration configuration) {
    super.onConfigurationChanged(configuration);

    // Do nothing.  We don't do orientation changes in the game screen.
    Log.d("ZombieRun.GameMapActivity", "onConfigurationChanged handled.");
  }
  
  protected static synchronized GoogleAnalyticsTracker getAnalyticsTracker(
      Context context) {
    if (tracker == null) {
      tracker = GoogleAnalyticsTracker.getInstance();
      tracker.start("UA-214814-13", context);
    }
    return tracker;
  }
  
  protected static void logToAnalytics(Context context) {
    GoogleAnalyticsTracker tracker = getAnalyticsTracker(context);
    String pageView = "/action/" + context.getClass().getSimpleName();
    Log.d("ZombieRun.BaseActivity", pageView);
    tracker.trackPageView(pageView);
    tracker.dispatch();
  }
}
