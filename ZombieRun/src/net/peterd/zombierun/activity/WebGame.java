package net.peterd.zombierun.activity;

import net.peterd.zombierun.R;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class WebGame extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    startActivity(
        new Intent()
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse(getString(R.string.web_game_url))));
  }
}
