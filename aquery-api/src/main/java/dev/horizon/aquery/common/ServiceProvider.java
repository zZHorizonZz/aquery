package dev.horizon.aquery.common;

import java.util.ServiceLoader;

/**
 * Finds the implementation of a service interface on the module path or the class path, and keeps it.
 *
 * <p>
 * The api module declares the parser interfaces. The internal module supplies their implementations. The first call
 * to {@link #get()} loads the implementation. The next calls give the same implementation.
 */
public final class ServiceProvider<T> {

  private final Class<T> service;
  private volatile T provided;

  public ServiceProvider(Class<T> service) {
    this.service = service;
  }

  /**
   * Gives the implementation of the service.
   *
   * @return the first implementation that the service loader finds
   * @throws IllegalStateException if no module supplies an implementation
   */
  public T get() {
    T result = provided;
    if (result == null) {
      result = ServiceLoader.load(service).findFirst().orElseThrow(() -> new IllegalStateException(
          "no " + service.getSimpleName() + " on the class path; add the aquery-internal module"));
      provided = result;
    }
    return result;
  }
}
