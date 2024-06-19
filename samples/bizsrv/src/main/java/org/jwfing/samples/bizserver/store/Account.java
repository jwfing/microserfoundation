package org.jwfing.samples.bizserver.store;

import io.vertx.core.json.JsonObject;

public class Account extends JsonObject {
  public static Account fromJson(JsonObject json) {
    return new Account(json);
  }
  public Account() {
    super();
  }
  public Account(JsonObject object) {
    super(object.getMap());
  }

  public String getName() {
    return getString("name");
  }
  public int getId() {
    return getInteger("id");
  }
  public void setName(String name) {
    put("name", name);
  }
  public void setId(int id) {
    put("id", id);
  }
  public String getPassword() {
    return getString("password");
  }
  public void setPassword(String passwd) {
    put("password", passwd);
  }
}
