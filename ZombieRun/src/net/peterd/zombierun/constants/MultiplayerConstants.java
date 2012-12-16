package net.peterd.zombierun.constants;

public class MultiplayerConstants {
  public static final String multiplayerBaseUrl = "http://zombie-run-game-server.appspot.com";
  // public static final String multiplayerBaseUrl = "http://gameserver.zrli.com";

  public enum MultiplayerUrls {
    CREATE_GAME("/game/create"),
    GET_GAME("/game/get"),
    JOIN_GAME("/game/join"),
    PUT_GAME("/game/put"),
    START_GAME("/game/start");
    
    private final String url;
    
    private MultiplayerUrls(String path) {
      url = multiplayerBaseUrl + path;
    }
    
    public String getUrl() {
      return url;
    }
  }
  
  public enum MultiplayerParameters {
    GAME_ID("gid"),
    PLAYER_ID("pid"),
    PLAYER_DATA("pd"),
    ZOMBIES_DATA("z"),
    SECRET_KEY("s");
    
    private final String param;
    
    private MultiplayerParameters(String param) {
      this.param = param;
    }
    
    public String getParam() {
      return param;
    }
  }
}
