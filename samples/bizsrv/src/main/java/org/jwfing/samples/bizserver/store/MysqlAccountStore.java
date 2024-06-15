package org.jwfing.samples.bizserver.store;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.mysqlclient.MySQLBuilder;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.sqlclient.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class MysqlAccountStore implements AccountStore {
  private static final Logger logger = LoggerFactory.getLogger(MysqlAccountStore.class);
  private static String COLUMN_ID = "Id";
  private static String COLUMN_NAME = "Name";
  private static String COLUMN_PASSWORD = "Password";
  private static String COLUMN_Age = "Age";

  private SqlClient client;

  @Override
  public Future<Account> signup(String name, String password) {
    Promise tmpFuture = Promise.promise();
    logger.debug("try to signup user, name/password=" + name + password);
    client.preparedQuery("INSERT INTO users(Name, Password) VALUES(?, ?)")
            .execute(Tuple.of(name, password))
            .onComplete(ar -> {
              logger.debug("sql execute finished.");
              if (ar.failed()) {
                logger.warn("failed to create user. cause: " + ar.cause().getMessage());
                tmpFuture.fail(ar.cause());
                return;
              }
              RowIterator<Row> rowIter = ar.result().iterator();
              int result = rowIter.hasNext()? rowIter.next().getInteger(0) : 0;
              Account account = new Account();
              account.setId(result);
              account.setName(name);
              logger.debug("succeed to create user: " + account);
              tmpFuture.complete(account);
            });
    return tmpFuture.future();
  }

  @Override
  public Future<Account> login(String name, String password) {
    Promise tmpFuture = Promise.promise();
    client.preparedQuery("SELECT * from users where Name=? and Password=?")
            .execute(Tuple.of(name, password)).onComplete(ar -> {
              if (ar.failed()) {
                logger.warn("failed to fetch user. cause: " + ar.cause().getMessage());
                tmpFuture.fail(ar.cause());
                return;
              }
              RowIterator<Row> rowIter = ar.result().iterator();
              if (rowIter.hasNext()) {
                tmpFuture.complete(genAccount(rowIter.next()));
              } else {
                tmpFuture.complete(new Account());
              }
    });
    return tmpFuture.future();
  }

  @Override
  public Future<List<Account>> query(String name) {
    Promise tmpFuture = Promise.promise();
    client.preparedQuery("SELECT * from users where name=?")
            .execute(Tuple.of(name)).onComplete(ar -> {
              if (ar.failed()) {
                logger.warn("failed to query users. cause: " + ar.cause().getMessage());
                tmpFuture.fail(ar.cause());
                return;
              }
              RowIterator<Row> rowIter = ar.result().iterator();
              List<Account> result = new ArrayList<>(ar.result().size());
              while(rowIter.hasNext()) {
                Row row = rowIter.next();
                result.add(genAccount(row));
              }
              tmpFuture.complete(result);
            });
    return tmpFuture.future();
  }

  private Account genAccount(Row row) {
    String name = row.getString(COLUMN_NAME);
    int uid = row.getInteger(COLUMN_ID);
    Account result = new Account();
    result.setId(uid);
    result.setName(name);
    return result;
  }

  @Override
  public Future<Void> close() {
    if (null == this.client) {
      return Future.succeededFuture();
    }
    Promise tmpFuture = Promise.promise();
    this.client.close(ar -> {
      if (ar.failed()) {
        logger.warn("failed to close mysqlClient. cause: " + ar.cause().getMessage());
        tmpFuture.fail(ar.cause());
      } else {
        tmpFuture.complete();
      }
    });
    this.client = null;
    return tmpFuture.future();
  }

  private MysqlAccountStore(SqlClient client) {
    this.client = client;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    private MySQLConnectOptions connectOptions = new MySQLConnectOptions();
    private PoolOptions poolOptions = new PoolOptions();

    private Builder() {
    }

    public Builder setPort(int port) {
      connectOptions.setPort(port);
      return this;
    }

    public Builder setHost(String host) {
      connectOptions.setHost(host);
      return this;
    }

    public Builder setDatabase(String database) {
      connectOptions.setDatabase(database);
      return this;
    }

    public Builder setUser(String user) {
      connectOptions.setUser(user);
      return this;
    }

    public Builder setPassword(String passwd) {
      connectOptions.setPassword(passwd);
      return this;
    }

    public Builder setMaxPoolSize(int size) {
      poolOptions.setMaxSize(size);
      return this;
    }

    public AccountStore build(Vertx vertx) {
      SqlClient client = MySQLBuilder.client().with(poolOptions).connectingTo(connectOptions).using(vertx)
              .build();
      return new MysqlAccountStore(client);
    }
  }

}
