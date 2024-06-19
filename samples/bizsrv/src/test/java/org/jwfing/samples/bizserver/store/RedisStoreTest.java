package org.jwfing.samples.bizserver.store;

import io.vertx.core.Vertx;
import junit.framework.TestCase;

import java.util.concurrent.CountDownLatch;

public class RedisStoreTest extends TestCase {
  private boolean testSucceed = false;

  protected void setUp() throws Exception {
    testSucceed = false;
  }

  protected void tearDown() throws Exception {
  }
  public void testReadAndWrite() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    Vertx vertx = Vertx.vertx();
    RedisStore.newBuilder().setEndpoint("redis://localhost:6379")
            .build(vertx)
            .onComplete(ar -> {
              if (ar.failed()) {
                latch.countDown();
                return;
              }
              RedisStore redisStore = ar.result();
              Account testAcnt = new Account();
              testAcnt.setName("test@abc.com");
              testAcnt.setPassword("password");
              redisStore.save("test1", testAcnt)
                      .compose(res -> {
                        System.out.println("save account to redis.");
                        return redisStore.retrieve("test1");
                      })
                      .onComplete(arr -> {
                        if (arr.failed()) {
                          latch.countDown();
                          return;
                        }
                        System.out.println("retrieve result from redis: " + arr.result());
                        testSucceed = true;
                        latch.countDown();
                        redisStore.close();
                      });
            });
    latch.await();
    assertTrue(testSucceed);
  }
}
