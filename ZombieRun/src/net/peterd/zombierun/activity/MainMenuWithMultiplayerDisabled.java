package net.peterd.zombierun.activity;

import net.peterd.zombierun.R;
import net.peterd.zombierun.game.GameSettings;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainMenuWithMultiplayerDisabled extends MainMenu {

  private void startGame() {
    Bundle bundle = new Bundle();
    GameSettings settings = Util.handleGameSettings(this, false);
    settings.toBundle(bundle);

    Intent startGameIntent = new Intent(this, StartGame.class);
    startGameIntent.putExtras(bundle);
    startActivity(startGameIntent);
  }

  @Override
  protected void setupMainLayout() {
    setContentView(R.layout.main_multiplayer_disabled);

    final Activity activity = this;
    ((Button) findViewById(R.id.button_start_multiplayer)).setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View v) {
            startActivity(new Intent(activity, WebGame.class));
          }
        });

    ((Button) findViewById(R.id.button_start_singleplayer)).setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View v) {
            if (testForHardwareCapabilitiesAndShowAlert()) {
              startGame();
            }
          }
        });

    Button helpButton = (Button) findViewById(R.id.button_help);
    final Intent showHelpIntent = new Intent(this, About.class);
    helpButton.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          startActivity(showHelpIntent);
        }
      });

    Util.configureAds(this);
  }
}
