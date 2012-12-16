package net.peterd.zombierun.activity;

import android.content.Intent;
import android.os.Bundle;

public class Main extends BaseActivity {

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    startActivity(new Intent(this, MainMenuWithMultiplayerDisabled.class));
  }
}
