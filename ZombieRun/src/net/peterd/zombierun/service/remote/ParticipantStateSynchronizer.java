package net.peterd.zombierun.service.remote;

import java.util.List;

import net.peterd.zombierun.entity.Destination;
import net.peterd.zombierun.entity.Player;
import net.peterd.zombierun.entity.Zombie;
import net.peterd.zombierun.entity.Zombie.ZombieListSerializer;
import net.peterd.zombierun.game.GameState;
import net.peterd.zombierun.service.GameEventBroadcaster;
import net.peterd.zombierun.util.Log;

public class ParticipantStateSynchronizer extends RemoteGameStateSynchronizer {

  public ParticipantStateSynchronizer(GameServerBridge.ServerData serverData,
      GameState state, GameServerBridge bridge, int synchronizationIntervalMs,
      GameEventBroadcaster eventBroadcaster) {
    super(serverData, state, bridge, synchronizationIntervalMs, eventBroadcaster);
  }

  @Override
  protected void invalidate() {
    Log.i("ZombieRun.GameOwnerStateSynchronizer", "Invalidate");
    GameServerBridge.ServerData data =
        bridge.put(gameId, playerId, secretKey, state.getPlayers().get(playerId).toString(), null);
    handleServerData(data, eventBroadcaster);
  }
  
  protected void handleServerData(GameServerBridge.ServerData data,
      GameEventBroadcaster eventBroadcaster) {
    if (data.destination != null) {
      Destination destination = Destination.fromString(data.destination);
      if (destination != null) {
        state.setDestination(destination);
      }
    }
    
    for (int i = 0; i < data.playerStrings.size(); ++i) {
      Player player =
          Player.fromString(data.playerStrings.get(i), state.getDestination(), eventBroadcaster);
      state.getPlayers().set(i, player);
    }
    
    if (data.zombieHorde != null) {
      List<Zombie> zombies =
          ZombieListSerializer.fromString(data.zombieHorde, state.getPlayers(), eventBroadcaster);
      state.getZombies().clear();
      state.getZombies().addAll(zombies);
    }
  }
}
