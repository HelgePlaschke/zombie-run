package net.peterd.zombierun.activity;

import net.peterd.zombierun.R;
import net.peterd.zombierun.constants.ApplicationConstants;
import net.peterd.zombierun.game.GameSettings;
import net.peterd.zombierun.util.GeoCalculationUtil;
import net.peterd.zombierun.util.Log;
import android.app.Activity;
import android.os.Handler;
import android.widget.Spinner;

import com.google.ads.AdSenseSpec;
import com.google.ads.AdViewListener;
import com.google.ads.GoogleAdView;
import com.google.ads.AdSenseSpec.AdType;
import com.google.android.apps.analytics.GoogleAnalyticsTracker;

public class Util {

  public static GameSettings handleGameSettings(Activity activity,
      boolean startingMultiplayerGame) {
    Spinner zombieSpeedSpinner = (Spinner) activity.findViewById(R.id.spinner_zombie_speed);
    int indexOfSpeed = zombieSpeedSpinner.getSelectedItemPosition();
    double zombieSpeedMph = Double.parseDouble(
        activity.getResources().getStringArray(
            R.array.zombie_speeds_average_speed_mph)[indexOfSpeed]);
    double zombieSpeed = GeoCalculationUtil.milesPerHourToMetersPerSecond(zombieSpeedMph);

    Spinner zombieCountSpinner = (Spinner) activity.findViewById(R.id.spinner_zombie_count);
    int indexOfDensity = zombieCountSpinner.getSelectedItemPosition();
    double averageDistanceBetweenZombiesMeters = Double.parseDouble(
        activity.getResources().getStringArray(
            R.array.zombie_counts_average_distance_between_zombie_meters)[indexOfDensity]);
    double zombieDensity =
        GeoCalculationUtil.itemPerDistanceToItemsPerSquareKilometer(
            averageDistanceBetweenZombiesMeters);

    return new GameSettings(zombieDensity, zombieSpeed, startingMultiplayerGame);
  }

  public static void configureAds(final Activity activity) {
    if (Log.loggingEnabled()) {
      Log.d("ZombieRun.Util",
          "Adsense Ads Keywords: " + activity.getString(R.string.adsense_keywords));
    }
    final GoogleAdView adView =
        (GoogleAdView) activity.findViewById(R.id.adview);
    final GoogleAnalyticsTracker tracker =
        BaseActivity.getAnalyticsTracker(activity);
    AdViewListener listener = new AdViewListener() {
          public void onStartFetchAd() {
            tracker.trackEvent("ads",
                "start_fetch",
                activity.getClass().getSimpleName(),
                1);
            tracker.dispatch();
          }
          public void onFinishFetchAd() {
            tracker.trackEvent("ads",
                "finish_fetch",
                activity.getClass().getSimpleName(),
                1);
            tracker.dispatch();
          }
          public void onClickAd() {
            tracker.trackEvent("ads",
                "click",
                activity.getClass().getSimpleName(),
                1);
            tracker.dispatch();
          }
        };
    adView.setAdViewListener(listener);

    new Handler().post(new Runnable() {
        public void run() {
          AdSenseSpec adSenseSpec =
              new AdSenseSpec(activity.getString(R.string.adsense_pub_id))
                  .setCompanyName(
                      activity.getString(R.string.adsense_company_name))
                  .setAppName(activity.getString(R.string.app_name))
                  .setKeywords(activity.getString(R.string.adsense_keywords))
                  .setChannel(activity.getString(R.string.adsense_channel))
                  .setAdType(AdType.TEXT_IMAGE)
                  .setWebEquivalentUrl(activity.getString(R.string.about_url))
                  .setAdTestEnabled(ApplicationConstants.testing())
                  .setExpandDirection(AdSenseSpec.ExpandDirection.TOP)
                  .setAdType(AdSenseSpec.AdType.TEXT_IMAGE)
                  .setAdFormat(AdSenseSpec.AdFormat.FORMAT_320x50);

          // Fetch Google ad.
          // PLEASE DO NOT CLICK ON THE AD UNLESS YOU ARE IN TEST MODE.
          // OTHERWISE, YOUR ACCOUNT MAY BE DISABLED.
          adView.showAds(adSenseSpec);
        }
      });
  }
}
