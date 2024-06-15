package org.jwfing.samples.bizserver;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.micrometer.MetricsService;
import org.jwfing.samples.proto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {
  private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
  private MetricsService metricsService = null;
  private HttpServer httpServer = null;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    metricsService = MetricsService.create(vertx);
    GrpcServer grpcServer = GrpcServer.server(vertx);
    grpcServer.callHandler(AccountMgrGrpc.getLoginMethod(), request -> {
      request.handler(authRequest -> {
        GrpcServerResponse<AuthRequest, LoginReply> response = request.response();
        AccountBrief ab = AccountBrief.newBuilder().setId(1).setName("test@user.name").build();
        LoginReply reply = LoginReply.newBuilder().setAccount(ab).build();
        response.end(reply);
        logger.debug("reply login request with fake result.");
      });
    });
    grpcServer.callHandler(AccountMgrGrpc.getSignupMethod(), request -> {
      request.handler(authRequest -> {
        GrpcServerResponse<AuthRequest, SignupReply> response = request.response();
        AccountBrief ab = AccountBrief.newBuilder().setId(0).setName("test@user.name").build();
        SignupReply reply = SignupReply.newBuilder().setAccount(ab).build();
        response.end(reply);
        logger.debug("reply signup request with fake result.");
      });
    });
    grpcServer.callHandler(AccountMgrGrpc.getFindMethod(), request -> {
      request.handler(findRequest -> {
        GrpcServerResponse<FindRequest, FindReply> response = request.response();
        AccountBrief ab = AccountBrief.newBuilder().setId(0).setName("test@user.name").build();
        AccountBrief ab2 = AccountBrief.newBuilder().setId(1).setName("test@user.name").build();
        FindReply reply = FindReply.newBuilder().setAccounts(0, ab).setAccounts(1, ab2).build();
        response.end(reply);
        logger.debug("reply find request with fake result.");
      });
    });

    httpServer = vertx.createHttpServer();
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
    logger.info("successful stop.");
    stopPromise.complete();
  }
}
