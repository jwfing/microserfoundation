package org.jwfing.samples.app;

import junit.framework.TestCase;

public class DummyTest extends TestCase {
  @Override
  protected void setUp() throws Exception {
    System.out.println("setUp");
  }

  @Override
  protected void tearDown() throws Exception {
    System.out.println("tearDown");
  }

  public void testHello() {
    Dummy dummy = new Dummy();
    dummy.hello("dummy");
  }
}
