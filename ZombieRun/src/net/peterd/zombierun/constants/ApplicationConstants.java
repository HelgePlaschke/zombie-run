package net.peterd.zombierun.constants;

public class ApplicationConstants {

  private static final boolean developing = false;

  public static boolean loggingEnabled() {
    return developing;
  }

  public static boolean testing() {
    return developing;
  }

  public static final boolean multiplayerEnabled = false;
}
