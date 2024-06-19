package org.jwfing.samples.gw;

import com.google.protobuf.Descriptors;
import io.grpc.MethodDescriptor;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.validation.builder.ValidationHandlerBuilder;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.json.schema.*;
import org.jwfing.samples.common.HttpStatus;
import org.jwfing.samples.common.HttpVerticle;
import org.jwfing.samples.proto.AccountBrief;
import org.jwfing.samples.proto.AccountMgrGrpc;
import org.jwfing.samples.proto.AuthRequest;
import org.jwfing.samples.proto.SignupReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.micrometer.MetricsService;

import java.text.SimpleDateFormat;
import java.util.Date;

import static io.vertx.ext.web.validation.builder.Bodies.json;
import static io.vertx.ext.web.validation.builder.Parameters.optionalParam;
import static io.vertx.ext.web.validation.builder.Parameters.param;
import static io.vertx.json.schema.common.dsl.Schemas.*;

public class MainVerticle extends HttpVerticle {
  private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
  private static final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
  private static final String API_VERSION_1_1 = "/1.1/";

  private HttpServer httpServer = null;
  private GrpcClient grpcClient = null;
  private long serverStartTime = 0l;
  private MetricsService metricsService = null;

  @Override
  public void start(Promise<Void> startFuture) throws Exception {
    metricsService = MetricsService.create(vertx);
    grpcClient = GrpcClient.client(vertx);

    HttpServerOptions httpServerOptions = new HttpServerOptions()
            .setTcpFastOpen(true).setTcpCork(true).setTcpQuickAck(true).setReusePort(true);
    httpServer = vertx.createHttpServer(httpServerOptions);
    Router router = Router.router(vertx);
    router.get("/ping").handler(this::healthcheck);
    router.get("/metrics").handler(this::outputMetrics);
    router.route("/static/*").handler(StaticHandler.create("static"));

    SchemaParser parser = SchemaParser.createDraft7SchemaParser(
            SchemaRouter.create(vertx, new SchemaRouterOptions())
    );

    Route apiRoute = router.route(API_VERSION_1_1 + "*");
    apiRoute.handler(LoggerHandler.create())
            .handler(CorsHandler.create().allowedMethod(HttpMethod.GET).allowedMethod(HttpMethod.POST)
                    .allowedMethod(HttpMethod.PUT).allowedMethod(HttpMethod.DELETE).allowedMethod(HttpMethod.OPTIONS)
                    .allowedMethod(HttpMethod.HEAD)
                    .allowedHeader("Access-Control-Request-Method").allowedHeader("Access-Control-Allow-Credentials")
                    .allowedHeader("Access-Control-Allow-Origin").allowedHeader("Access-Control-Allow-Headers")
                    .allowedHeader("Content-Type").allowedHeader("Origin").allowedHeader("Accept"))
            .handler(BodyHandler.create().setBodyLimit(4 * 1024 * 1024l));
    apiRoute.failureHandler(failureRoutingContext -> {
      int statusCode = failureRoutingContext.statusCode();
      // Status code will be 500 for the RuntimeException
      // or 403 for the other failure
      HttpServerResponse response = failureRoutingContext.response();
      response.setStatusCode(statusCode).end(failureRoutingContext.failure().getMessage());
    });

    router.post(API_VERSION_1_1 + "users")
            .handler(ValidationHandlerBuilder.create(parser)
                    .body(json(objectSchema()
                            .requiredProperty("name", stringSchema())
                            .requiredProperty("password", stringSchema())))
                    .build())
            .handler(this::userSignup);
    router.post(API_VERSION_1_1 + "login")
            .handler(ValidationHandlerBuilder.create(parser)
                    .body(json(objectSchema()
                            .requiredProperty("name", stringSchema())
                            .requiredProperty("password", stringSchema())))
                    .build())
            .handler(this::userSignin);
    router.get(API_VERSION_1_1 + "users")
            .handler(ValidationHandlerBuilder.create(parser)
                    .queryParameter(param("name", stringSchema()))
                    .queryParameter(optionalParam("from", intSchema()))
                    .queryParameter(optionalParam("to", intSchema()))
                    .build())
            .handler(this::userFind);

    int portNum = 8080;
    httpServer.requestHandler(router).listen(portNum, ar->{
      if (ar.failed()) {
        logger.error("can NOT start a http server, cause: ", ar.cause());
        startFuture.fail(ar.cause());
      } else {
        serverStartTime = System.currentTimeMillis();
        logger.info("http server start at " + getDateString(serverStartTime) + ", listening on port " + portNum);
        startFuture.complete();
      }
    });
  }

  @Override
  public void stop(Promise<Void> stopFuture) throws Exception {
    if (null != httpServer) {
      httpServer.close(ar -> {
        logger.info("stop MainVerticle...");
        stopFuture.complete();
      });
    } else {
      logger.info("stop MainVerticle...");
      stopFuture.complete();
    }
  }

  private String getDateString(long dateTimeStamp) {
    return dateFormat.format(new Date(dateTimeStamp));
  }

  private JsonObject convert2Json(AccountBrief ab) {
    JsonObject result = new JsonObject();
    result.put("name", ab.getName());
    result.put("id", ab.getId());
    return result;
  }

  private void userSignup(RoutingContext context) {
    JsonObject requestBody = RequestParser.parse(context);
    String name = requestBody.getString("name", "");
    String password = requestBody.getString("password", "");
    SocketAddress serverAddr = SocketAddress.inetSocketAddress(8090, "127.0.0.1");

    logger.debug("try to signup with para: " + requestBody);

    grpcClient.request(serverAddr, AccountMgrGrpc.getSignupMethod()).compose(request -> {
      request.end(AuthRequest.newBuilder().setName(name).setPassword(password).build());
      return request.response().compose(response -> response.last());
    }).onSuccess(reply -> {
      JsonObject ab = convert2Json(reply.getAccount());
      logger.debug("signup Received " + ab.toString());
      ok(context, ab);
    });
  }

  private void userSignin(RoutingContext context) {
    JsonObject requestBody = RequestParser.parse(context);
    String name = requestBody.getString("name", "");
    String password = requestBody.getString("password", "");
    SocketAddress serverAddr = SocketAddress.inetSocketAddress(8090, "127.0.0.1");

    logger.debug("try to signin with para: " + requestBody);

    grpcClient.request(serverAddr, AccountMgrGrpc.getLoginMethod()).compose(request -> {
      request.end(AuthRequest.newBuilder().setName(name).setPassword(password).build());
      return request.response().compose(response -> response.last());
    }).onSuccess(reply -> {
      JsonObject ab = convert2Json(reply.getAccount());
      logger.debug("signin Received " + ab.toString());
      ok(context, ab);
    });
  }

  private void userFind(RoutingContext context) {
    JsonObject requestBody = RequestParser.parse(context);
    ok(context, new JsonObject());
  }

  private void healthcheck(RoutingContext context) {
    long currentTs = System.currentTimeMillis();
    long upTimeInMinute = (currentTs - serverStartTime) / 60000;
    long upTimeInHour = 0;
    if (upTimeInMinute > 60) {
      upTimeInHour = upTimeInMinute / 60l;
      upTimeInMinute = upTimeInMinute % 60;
    }
    JsonObject result = new JsonObject().put("status", "green")
            .put("availableProcessors", Runtime.getRuntime().availableProcessors())
            .put("freeMemory", Runtime.getRuntime().freeMemory())
            .put("totalMemory", Runtime.getRuntime().totalMemory())
            .put("maxMemory", Runtime.getRuntime().maxMemory())
            .put("activeThreads", Thread.activeCount())
            .put("serverStart", getDateString(serverStartTime))
            .put("upTime", String.format("%d hours %d minutes", upTimeInHour, upTimeInMinute));
    ok(context, result);
  }

  private void outputMetrics(RoutingContext context) {
    JsonObject metrics = metricsService.getMetricsSnapshot();
    ok(context, metrics);
  }
}
