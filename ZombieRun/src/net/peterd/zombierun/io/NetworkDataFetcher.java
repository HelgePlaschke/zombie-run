package net.peterd.zombierun.io;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.Map;

public class NetworkDataFetcher implements DataFetcher {

  public BufferedReader getData(String url) throws IOException {
    Map<String, String> emptyMap = Collections.emptyMap();
    return getData(url, emptyMap);
  }

  public BufferedReader getData(String url, Map<String, String> postVariables) throws IOException {
    URL u = new URL(url);
    URLConnection connection = u.openConnection();
    connection.addRequestProperty("User-Agent", "ZombieRun");
    buildPostData(connection, postVariables);
    InputStream content = connection.getInputStream();
    return new BufferedReader(new InputStreamReader(content));
  }

  private void buildPostData(URLConnection connection, Map<String, String> postVariables)
      throws IOException {
    connection.setDoInput(true);
    connection.setDoOutput(true);
    connection.setUseCaches(false);
    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    if (postVariables.size() > 1) {
      StringBuilder postDataBuilder = new StringBuilder();
      int i = 0;
      for (Map.Entry<String, String> entry : postVariables.entrySet()) {
        if (i != 0) {
          postDataBuilder.append("&");
        }
        postDataBuilder.append(entry.getKey());
        postDataBuilder.append("=");
        postDataBuilder.append(entry.getValue());
        i++;
      }
      DataOutputStream output = new DataOutputStream(connection.getOutputStream());
      output.writeBytes(postDataBuilder.toString());
      output.flush();
      output.close();
    }
  }
}
