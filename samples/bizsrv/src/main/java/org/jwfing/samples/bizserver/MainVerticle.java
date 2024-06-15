package org.jwfing.samples.bizserver;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.micrometer.MetricsService;
import org.jwfing.samples.bizserver.store.Account;
import org.jwfing.samples.bizserver.store.AccountStore;
import org.jwfing.samples.bizserver.store.MysqlAccountStore;
import org.jwfing.samples.proto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MainVerticle extends AbstractVerticle {
  private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
  private MetricsService metricsService = null;
  private HttpServer httpServer = null;
  private AccountStore accountStore = null;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    metricsService = MetricsService.create(vertx);
    GrpcServer grpcServer = GrpcServer.server(vertx);
    grpcServer.callHandler(AccountMgrGrpc.getLoginMethod(), request -> {
      request.handler(authRequest -> {
        GrpcServerResponse<AuthRequest, LoginReply> response = request.response();
        accountStore.login(authRequest.getName(), authRequest.getPassword())
                .onComplete(ar -> {
                  if (ar.failed()) {
                    logger.warn("failed to login user. cause: " + ar.cause().getMessage());
                    LoginReply reply = LoginReply.newBuilder().build();
                    response.end(reply);
                    return;
                  }
                  Account target = ar.result();
                  AccountBrief ab = AccountBrief.newBuilder()
                          .setId(target.getId()).setName(target.getName()).build();
                  LoginReply reply = LoginReply.newBuilder().setAccount(ab).build();
                  response.end(reply);
                });
      });
    });

    grpcServer.callHandler(AccountMgrGrpc.getSignupMethod(), request -> {
      logger.debug("start signup rpc...");
      request.handler(authRequest -> {
        GrpcServerResponse<AuthRequest, SignupReply> response = request.response();
        accountStore.signup(authRequest.getName(), authRequest.getPassword())
                .onComplete(ar -> {
                  logger.debug("complete signup rpc...");
                  if (ar.failed()) {
                    logger.warn("failed to signup user. cause: " + ar.cause().getMessage());
                    SignupReply reply = SignupReply.newBuilder().build();
                    response.end(reply);
                    return;
                  }
                  Account target = ar.result();
                  logger.debug("accountStore signup result: " + target);
                  AccountBrief ab = AccountBrief.newBuilder()
                          .setId(target.getId())
                          .setName(target.getName())
                          .build();
                  SignupReply reply = SignupReply.newBuilder().setAccount(ab).build();
                  response.end(reply);
                });
      });
    });
    grpcServer.callHandler(AccountMgrGrpc.getFindMethod(), request -> {
      request.handler(findRequest -> {
        GrpcServerResponse<FindRequest, FindReply> response = request.response();
        accountStore.query(findRequest.getName())
                .onComplete(ar -> {
                  if (ar.failed()) {
                    logger.warn("failed to query users. cause: " + ar.cause().getMessage());
                    FindReply reply = FindReply.newBuilder().build();
                    response.end(reply);
                    return;
                  }
                  List<Account> results = ar.result();
                  FindReply.Builder replyBuilder = FindReply.newBuilder();
                  int i = 0;
                  for (Account account: results) {
                    AccountBrief ab = AccountBrief.newBuilder()
                            .setId(account.getId())
                            .setName(account.getName()).build();
                    replyBuilder.setAccounts(i, ab);
                    i++;
                  }
                  response.end(replyBuilder.build());
                });
      });
    });

    httpServer = vertx.createHttpServer();
    accountStore = MysqlAccountStore.newBuilder()
            .setPort(13306).setHost("127.0.0.1").setDatabase("uluru")
            .setUser("test").setPassword("itsnothing")
            .setMaxPoolSize(5)
            .build(vertx);
    int port = 8090;
    httpServer.requestHandler(grpcServer)
            .listen(port, ar -> {
              if (ar.failed()) {
                logger.error("failed to start http server. cause: " + ar.cause().getMessage());
                startPromise.fail(ar.cause());
              } else {
                logger.info("successful start. listen on port: " + port);
                startPromise.complete();
              }
            });
  }

  @Override
  public void stop(Promise<Void> stopPromise) throws Exception {
    if (null != httpServer) {
      httpServer.close();
    }
    if (null != accountStore) {
      accountStore.close();
    }
    logger.info("successful stop.");
    stopPromise.complete();
  }
}
