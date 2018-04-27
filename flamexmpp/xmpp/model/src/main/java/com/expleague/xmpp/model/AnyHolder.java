package com.expleague.xmpp.model;

import com.expleague.xmpp.model.control.receipts.Request;

import java.util.List;
import java.util.function.Predicate;

/**
 * Experts League
 * Created by solar on 13/03/16.
 */
public interface AnyHolder {
  List<? super Item> any();

  default <T> T get(Class<T> clazz) {
    //noinspection unchecked
    return (T) this.any().stream().filter(x -> clazz.isAssignableFrom(x.getClass())).findAny().orElse(null);
  }

  default <S extends AnyHolder, T extends Item> S append(T o) {
    any().add(o);
    //noinspection unchecked
    return (S) this;
  }

  default boolean has(Class<?> clazz) {
    return any().stream().anyMatch(x -> x != null && clazz.isAssignableFrom(x.getClass()));
  }

  default <T extends Item> boolean has(Class<T> clazz, Predicate<T> filter) {
    //noinspection unchecked
    return any().stream()
      .filter(x -> x != null && clazz.isAssignableFrom(x.getClass()))
      .anyMatch(x -> filter.test((T) x));
  }

  default void remove(Class<Request> clazz) {
    any().removeIf(item -> clazz.isAssignableFrom(item.getClass()));
  }

}
