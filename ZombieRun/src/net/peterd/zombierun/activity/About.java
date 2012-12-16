package net.peterd.zombierun.activity;

import net.peterd.zombierun.R;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class About extends BaseActivity {
  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.about_url)));
    startActivity(intent);
    startActivity(new Intent(this, Main.class));
  }
}
