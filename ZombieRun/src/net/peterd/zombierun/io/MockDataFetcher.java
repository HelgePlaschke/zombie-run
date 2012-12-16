package net.peterd.zombierun.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

public class MockDataFetcher implements DataFetcher {

  private Map<String, String> simpleResponses;
  private Map<Pair<String, Map<String, String>>, String> postResponses;
  
  public Map<String, String> getMutableSimpleResponses() {
    return simpleResponses;
  }
  
  public Map<Pair<String, Map<String, String>>, String> getMutablePostResponses() {
    return postResponses;
  }
  
  public BufferedReader getData(String url) throws IOException {
    String data = simpleResponses.get(url);
    if (data == null) {
      throw new IOException("Response not set for url '" + url + "'.");
    } else {
      return new BufferedReader(new StringReader(data));
    }
  }
  
  public BufferedReader getData(String url, Map<String, String> postVariables) throws IOException {
    String data = postResponses.get(Pair.newPair(url, postVariables));
    if (data == null) {
      throw new IOException("Response not set for url '" + url + "' and post variables " +
          postVariables.toString() + ".");
    } else {
      return new BufferedReader(new StringReader(data));
    }
  }

  public static class Pair<T, V> {
    private final T t;
    private final V v;
    
    public Pair(T t, V v) {
      this.t = t;
      this.v = v;
    }
    
    public T first() {
      return t;
    }
    
    public V second() {
      return v;
    }
    
    public static <T, V> Pair<T, V> newPair(T t, V v) {
      return new Pair<T, V>(t, v);
    }
  }
}
