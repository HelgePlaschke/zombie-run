package net.peterd.zombierun.activity;

import net.peterd.zombierun.entity.Destination;
import net.peterd.zombierun.service.GameService;

public class SinglePlayerGame extends Game {

  @Override
  protected void createGame(GameService service, Destination destination) {
    service.createSinglePlayerGame(service.getHardwareManager().getLastKnownLocation(),
        destination,
        gameSettings);
  }
}
