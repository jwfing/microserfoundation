package org.jwfing.samples.gw;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.jwfing.samples.common.StringUtils;

import java.util.Map;
import java.util.stream.Collectors;

public class RequestParser {
  public static JsonObject parse(RoutingContext context) throws IllegalArgumentException {
    HttpMethod httpMethod = context.request().method();
    JsonObject body;
    if (HttpMethod.GET.equals(httpMethod)) {
      Map<String, String> filteredEntries = context.request().params().entries()
              .stream()
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
      body = JsonObject.mapFrom(filteredEntries);
    } else if (HttpMethod.PUT.equals(httpMethod) || HttpMethod.POST.equals(httpMethod)) {
      try {
        String bodyString = context.getBodyAsString();
        if (StringUtils.isEmpty(bodyString)) {
          body = new JsonObject();
        } else {
          body = new JsonObject(bodyString);
        }
      } catch (DecodeException exception) {
        throw new IllegalArgumentException("request body is invalid(CODE: INVALID_JSON_FORMAT)");
      }
    } else {
      String bodyString = context.getBodyAsString();
      if (StringUtils.isEmpty(bodyString)) {
        body = new JsonObject();
      } else {
        try {
          body = new JsonObject(bodyString);
        } catch (DecodeException exception) {
          throw new IllegalArgumentException("request body is invalid(CODE: INVALID_JSON_FORMAT)");
        }
      }
    }
    return body;
  }
}
