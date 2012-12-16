package net.peterd.zombierun.service.remote;

import net.peterd.zombierun.entity.Zombie.ZombieListSerializer;
import net.peterd.zombierun.game.GameState;
import net.peterd.zombierun.service.GameEventBroadcaster;
import net.peterd.zombierun.util.Log;

public class GameOwnerStateSynchronizer extends ParticipantStateSynchronizer {

  public GameOwnerStateSynchronizer(GameServerBridge.ServerData serverData, GameState state,
      GameServerBridge bridge, int synchronizationIntervalMs,
      GameEventBroadcaster eventBroadcaster) {
    super(serverData, state, bridge, synchronizationIntervalMs, eventBroadcaster);
  }

  @Override
  protected void invalidate() {
    Log.i("ZombieRun.GameOwnerStateSynchronizer", "Invalidate");
    GameServerBridge.ServerData data =
        bridge.put(gameId,
            playerId,
            secretKey,
            state.getPlayers().get(playerId).toString(),
            ZombieListSerializer.toString(state.getZombies()));
    handleServerData(data, eventBroadcaster);
  }
}
