package org.jwfing.samples.common;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class HttpVerticle extends AbstractVerticle {
  public static final String HEADER_CONTENT_TYPE = "Content-Type";
  public static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";

  protected void response(RoutingContext context, int status, JsonObject header, JsonObject result) {
    response(context, status, header, Json.encodePrettily(result));
  }

  protected void response(RoutingContext context, int status, JsonObject header, String result) {
    try {
      String origin = context.request().getHeader("Origin");
      HttpServerResponse response = context.response().setStatusCode(status)
              .putHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
              .putHeader("Access-Control-Allow-Origin", StringUtils.isEmpty(origin) ? "*" : origin);
      if (null != header) {
        header.stream().sequential().forEach(entry -> response.putHeader(entry.getKey(), (String) entry.getValue()));
      }
      response.end(result);
    } catch (IllegalStateException ex) {
      // Response is closed.
    }
  }

  protected void ok(RoutingContext context, JsonObject result) {
    response(context, HttpStatus.SC_OK, null, result);
  }

  protected void ok(RoutingContext context, String result) {
    response(context, HttpStatus.SC_OK, null, result);
  }
}
