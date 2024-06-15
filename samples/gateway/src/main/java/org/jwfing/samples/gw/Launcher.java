package org.jwfing.samples.gw;

import io.vertx.core.VertxOptions;
import io.vertx.tracing.zipkin.ZipkinTracingOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxInfluxDbOptions;

public class Launcher extends io.vertx.core.Launcher {
  private static final Logger logger = LoggerFactory.getLogger(Launcher.class);
  private static final String INFLUXDB_METRICS_ENABLE = "vertx.metrics.options.influx.enabled";
  private static final String INFLUXDB_METRICS_URI = "vertx.metrics.options.influx.uri";
  private static final String INFLUXDB_METRICS_DATABASE = "vertx.metrics.options.influx.db";

  @Override
  public void beforeStartingVertx(VertxOptions options) {
    MicrometerMetricsOptions metricsOptions = new MicrometerMetricsOptions().setEnabled(true);
    String influxMetricsEnable = System.getProperty(INFLUXDB_METRICS_ENABLE);
    if ("true".equalsIgnoreCase(influxMetricsEnable)) {
      String influxUri = System.getProperty(INFLUXDB_METRICS_URI, "http://localhost:8086");
      String influxDb = System.getProperty(INFLUXDB_METRICS_DATABASE, "ayers");
      VertxInfluxDbOptions influxDbOptions = new VertxInfluxDbOptions().setUri(influxUri)
              .setDb(influxDb).setEnabled(true);
      metricsOptions.setInfluxDbOptions(influxDbOptions);
      logger.info("vertx.metrics.options.influx.options: uri=" + influxUri + ", db=" + influxDb);
    } else {
      logger.info("vertx.metrics.options.influx.enabled: false");
    }
    options.setMetricsOptions(metricsOptions);
    options.setTracingOptions(new ZipkinTracingOptions().setServiceName("gateway service"));
  }

  public static void main(String[] args) {
    logger.info("main invoked.");
    (new Launcher()).dispatch(args);
  }

  public static void executeCommand(String cmd, String... args) {
    logger.info("executeCommand invoked.");
    (new Launcher()).execute(cmd, args);
  }
}
