package net.peterd.zombierun.service;

import net.peterd.zombierun.game.GameEvent;

public interface GameEventListener {

  public void receiveEvent(GameEvent event);
}
