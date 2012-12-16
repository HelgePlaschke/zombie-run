package net.peterd.zombierun.service.remote;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.peterd.zombierun.util.Log;

import net.peterd.zombierun.constants.MultiplayerConstants;
import net.peterd.zombierun.io.DataFetcher;

public class GameServerBridge {
  
  private final DataFetcher fetcher;
  
  public GameServerBridge(DataFetcher fetcher) {
    this.fetcher = fetcher;
  }
  
  private ServerData getData(String url) {
    Map<String, String> params = Collections.emptyMap();
    return getData(url, params);
  }
  
  private ServerData getData(String url, Map<String, String> params) {
    ServerData data = new ServerData();
    try {
      BufferedReader reader = fetcher.getData(url, params);
      String line;
      while ((line = reader.readLine()) != null) {
        String[] splits = line.split(":", 1);
        if (splits.length != 2) {
          Log.e("ZombieRun.GameServerBridge",
              "Could not split game server response line '" + line + "' into a key:value pair.");
        }
        String key = splits[0];
        String value = splits[1];
        populateServerDataWithLine(data, line, key, value);
      }
    } catch (IOException e) {
      Log.e("ZombieRun.GameServerBridge", "IOError while fetching base URL '" + url + "'.", e);
    }
    return data;
  }

  private void populateServerDataWithLine(ServerData data, String line, String key, String value) {
    if (key.equals("player_id")) {
      data.playerId = Integer.parseInt(value);
    } else if (key.equals("game_id")) {
      data.gameId = Integer.parseInt(value);
    } else if (key.equals("secret")) {
      data.secret = value;
    } else if (key.equals("started")) {
      if (value.equals("1")) {
        data.started = true;
      } else {
        data.started = false;
      }
    } else if (key.equals("destination")) {
      data.destination = value;
    } else if (key.equals("players[]")) {
      data.playerStrings.add(value);
    } else if (key.equals("zombies")) {
      data.zombieHorde = value;
    } else {
      Log.w("ZombieRun.GameServerBridge", "GameServer returned line that we were unable to " +
      		"interpret: '" + line + "'.");
    }
  }
  
  public ServerData create() {
    return getData(MultiplayerConstants.MultiplayerUrls.CREATE_GAME.getUrl());
  }
  
  public ServerData get(Integer gameId, Integer playerId, String secret) {
    Map<String, String> parameters = new HashMap<String, String>();
    parameters.put(MultiplayerConstants.MultiplayerParameters.GAME_ID.getParam(),
        gameId.toString());
    parameters.put(MultiplayerConstants.MultiplayerParameters.PLAYER_ID.getParam(),
        playerId.toString());
    parameters.put(MultiplayerConstants.MultiplayerParameters.SECRET_KEY.getParam(), secret);
    return getData(MultiplayerConstants.MultiplayerUrls.GET_GAME.getUrl(), parameters);
  }
  
  public ServerData join(Integer gameId) {
    Map<String, String> parameters = new HashMap<String, String>();
    parameters.put(MultiplayerConstants.MultiplayerParameters.GAME_ID.getParam(),
        gameId.toString());
    return getData(MultiplayerConstants.MultiplayerUrls.GET_GAME.getUrl(), parameters);
  }
  
  /**
   * zombieHorde may be null.
   * 
   * @param gameId
   * @param playerId
   * @param secret
   * @param playerData
   * @param zombieHorde
   * @return
   */
  public ServerData put(Integer gameId, Integer playerId, String secret, String playerData,
      String zombieHorde) {
    Map<String, String> parameters = new HashMap<String, String>();
    parameters.put(MultiplayerConstants.MultiplayerParameters.GAME_ID.getParam(),
        gameId.toString());
    parameters.put(MultiplayerConstants.MultiplayerParameters.PLAYER_ID.getParam(),
        playerId.toString());
    parameters.put(MultiplayerConstants.MultiplayerParameters.SECRET_KEY.getParam(), secret);
    parameters.put(MultiplayerConstants.MultiplayerParameters.PLAYER_DATA.getParam(), playerData);
    if (zombieHorde != null) {
      parameters.put(MultiplayerConstants.MultiplayerParameters.ZOMBIES_DATA.getParam(),
          zombieHorde);
    }
    return getData(MultiplayerConstants.MultiplayerUrls.PUT_GAME.getUrl(), parameters);
  }
  
  public ServerData start(Integer gameId, String secret) {
    Map<String, String> parameters = new HashMap<String, String>();
    parameters.put(MultiplayerConstants.MultiplayerParameters.GAME_ID.getParam(),
        gameId.toString());
    parameters.put(MultiplayerConstants.MultiplayerParameters.PLAYER_ID.getParam(), "0");
    parameters.put(MultiplayerConstants.MultiplayerParameters.SECRET_KEY.getParam(), secret);
    return getData(MultiplayerConstants.MultiplayerUrls.GET_GAME.getUrl(), parameters);
  }
  
  public static class ServerData {
    public int gameId;
    public int playerId;
    public boolean started;
    public String secret;
    public String zombieHorde;
    public String destination;
    public final List<String> playerStrings = new ArrayList<String>();
  }
}
