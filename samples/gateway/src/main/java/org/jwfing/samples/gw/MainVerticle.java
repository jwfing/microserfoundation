package org.jwfing.samples.gw;

import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.jwfing.samples.common.HttpVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.micrometer.MetricsService;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MainVerticle extends HttpVerticle {
  private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
  private static final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

  private HttpServer httpServer = null;
  private long serverStartTime = 0l;
  private MetricsService metricsService = null;

  @Override
  public void start(Promise<Void> startFuture) throws Exception {
    HttpServerOptions httpServerOptions = new HttpServerOptions()
            .setTcpFastOpen(true).setTcpCork(true).setTcpQuickAck(true)
            .addWebSocketSubProtocol("lc.protobuf2.1")
            .addWebSocketSubProtocol("lc.protobuf2.2")
            .addWebSocketSubProtocol("lc.protobuf2.3")
            .setReusePort(true);
    httpServer = vertx.createHttpServer(httpServerOptions);
    Router router = Router.router(vertx);
    router.get("/ping").handler(this::healthcheck);
    router.get("/metrics").handler(this::outputMetrics);

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
