package org.jwfing.samples.bizserver.store;

import io.vertx.core.Future;

import java.util.List;

public interface AccountStore {
  Future<Account> signup(String name, String password);
  Future<Account> login(String name, String password);
  Future<List<Account>> query(String name);
  Future<Void> close();
}
