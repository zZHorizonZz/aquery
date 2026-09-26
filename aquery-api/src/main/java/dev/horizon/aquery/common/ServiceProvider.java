package dev.horizon.aquery.common;

import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Finds the implementation of a service interface on the module path or the class path, and keeps it.
 *
 * <p>
 * The api module declares the parser interfaces. The internal module supplies their implementations. The first call
 * to {@link #get()} loads the implementation. The next calls give the same implementation.
 *
 * <p>
 * The lookup tries these locations in sequence, and takes the first implementation that it finds:
 *
 * <ul>
 * <li>The module layer of the service interface, if the interface is in a named module. A plugin that loads the
 * library in its own module layer thus finds the implementation in that layer.
 * <li>The class loader of the service interface.
 * <li>The context class loader of the current thread.
 * </ul>
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
   * @return the first implementation that the lookup finds
   * @throws IllegalStateException if no module supplies an implementation
   */
  public T get() {
    T result = provided;
    if (result == null) {
      result = find().orElseThrow(() -> new IllegalStateException(
          "no " + service.getSimpleName() + " on the module path or the class path; add the aquery-internal module"));
      provided = result;
    }
    return result;
  }

  private Optional<T> find() {
    Module module = service.getModule();
    Optional<T> found = Optional.empty();
    if (module.isNamed() && module.getLayer() != null) {
      found = ServiceLoader.load(module.getLayer(), service).findFirst();
    }
    return found.or(() -> ServiceLoader.load(service, service.getClassLoader()).findFirst())
        .or(() -> ServiceLoader.load(service).findFirst());
  }
}
