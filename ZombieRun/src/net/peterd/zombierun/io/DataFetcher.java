package net.peterd.zombierun.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

public interface DataFetcher {

  BufferedReader getData(String url) throws IOException;
  
  BufferedReader getData(String url, Map<String, String> postVariables) throws IOException;
}
