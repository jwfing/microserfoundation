package org.jwfing.samples.bizserver.store;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;

import java.util.Arrays;

public class RedisStore {
  private Redis redisClient = null;
  private RedisStore(Redis redis) {
    this.redisClient = redis;
  }

  public void close() {
    if (null != redisClient) {
      this.redisClient.close();
    }
  }

  public Future<Account> retrieve(String key) {
    Promise<Account> promise = Promise.promise();
    RedisAPI redis = RedisAPI.api(this.redisClient);
    redis.get(key).onComplete(res -> {
      if (res.failed()) {
        promise.fail(res.cause());
        return;
      }
      String response = res.result().toString();
      JsonObject resJson = new JsonObject(response);
      promise.complete(Account.fromJson(resJson));
    });
    return promise.future();
  }

  public Future<Void> save(String key, Account account) {
    Promise<Void> promise = Promise.promise();
    RedisAPI redis = RedisAPI.api(this.redisClient);
    redis.set(Arrays.asList(key, account.toString())).onComplete(res -> {
      if (res.failed()) {
        promise.fail(res.cause());
        return;
      }
      promise.complete();
    });
    return promise.future();
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    private String endpoint = null;

    public Future<RedisStore> build(Vertx vertx) {
      Promise<RedisStore> promise = Promise.promise();
      RedisOptions options = new RedisOptions();
      options.setEndpoint(this.endpoint);
      options.setMaxPoolSize(4);
      Redis client = Redis.createClient(vertx, options);
      client.connect()
              .onSuccess(conn -> {
                // use the connection
                promise.complete(new RedisStore(client));
              }).onFailure(throwable -> {
                promise.fail(throwable);
      });
      return promise.future();
    }
    public Builder setEndpoint(String endpoint) {
      this.endpoint = endpoint;
      return this;
    }
    private Builder() {
    }
  }
}
