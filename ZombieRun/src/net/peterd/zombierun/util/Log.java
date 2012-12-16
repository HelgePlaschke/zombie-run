package net.peterd.zombierun.util;

import net.peterd.zombierun.constants.ApplicationConstants;

public class Log {
  
  public static boolean loggingEnabled() {
    return ApplicationConstants.loggingEnabled();
  }

  /**
   * Log to debug level severity.  If in a critical section, allocating memory
   * for strings or doing "" + "" -style string concatenation can be optimized
   * out in the release binary by first checking Log.loggingEnabled();
   * 
   * @param tag
   * @param message
   */
  public static void d(String tag, String message) {
    if (loggingEnabled()) {
      android.util.Log.d(tag, message);
    }
  }
  
  public static void e(String tag, String message) {
    android.util.Log.e(tag, message);
  }
  
  public static void w(String tag, String message) {
    android.util.Log.w(tag, message);
  }
  
  public static void e(String tag, String message, Exception e) {
    android.util.Log.e(tag, message, e);
  }
  
  public static void i(String tag, String message) {
    if (loggingEnabled()) {
      android.util.Log.i(tag, message);
    }
  }
  
  public static void println(int severity, String tag, String message) {
    if (ApplicationConstants.loggingEnabled()) {
      android.util.Log.println(severity, tag, message);
    }
  }
}
