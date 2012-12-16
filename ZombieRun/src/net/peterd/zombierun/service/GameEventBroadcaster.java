package net.peterd.zombierun.service;

import net.peterd.zombierun.game.GameEvent;

public interface GameEventBroadcaster {
  
  public void broadcastEvent(GameEvent event);
}
