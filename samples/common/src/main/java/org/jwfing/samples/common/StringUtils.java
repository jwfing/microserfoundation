package org.jwfing.samples.common;

public class StringUtils {
  public static boolean isEmpty(String str) {
    return null == str || str.length() < 1;
  }
}
