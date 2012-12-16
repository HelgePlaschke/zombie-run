package net.peterd.zombierun.activity;

import net.peterd.zombierun.R;
import net.peterd.zombierun.service.HardwareManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;

public abstract class MainMenu extends BaseActivity {
  
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    if (state != null) {
      onRestoreInstanceState(state);
    }
    setupMainLayout();
    testForHardwareCapabilitiesAndShowAlert();
  }
  
  /**
   * Setup the layout and handle all UI elements.
   */
  protected abstract void setupMainLayout();
  
  protected boolean mayStartGame() {
    return testForHardwareCapabilities();
  }
  
  /**
   * Returns true if all the hardware was properly initialized. 
   */
  protected boolean testForHardwareCapabilitiesAndShowAlert() {
    // TODO: Use the actual error message, so that we can determine which of the hardware is
    // disabled or absent.
    if (!testForHardwareCapabilities()) {
      showEnableGPSAlert();
      return false;
    }
    return true;
  }
  
  /**
   * @return true if all the hardware was properly initialized.
   */
  private boolean testForHardwareCapabilities() {
    // TODO: Use the actual error message, so that we can determine which of the hardware is
    // disabled or absent.
    HardwareManager hardwareManager = new HardwareManager(this);
    Integer errorMessage = hardwareManager.initializeHardware();
    if (errorMessage != null) {
      return false;
    }
    return true;
  }
  
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      Intent mainScreenIntent = new Intent(Intent.ACTION_MAIN);
      mainScreenIntent.addCategory(Intent.CATEGORY_HOME);
      startActivity(mainScreenIntent);
    }
    return false;
  }
  
  private void showEnableGPSAlert() {
    new AlertDialog.Builder(this)
        .setMessage(R.string.error_gps_disabled)
        .setPositiveButton(R.string.enable_gps,
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
              }
            })
        .show();
  }
}
