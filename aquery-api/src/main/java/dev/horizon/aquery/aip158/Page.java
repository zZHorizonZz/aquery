package dev.horizon.aquery.aip158;

import java.util.List;
import java.util.Objects;

/**
 * One page of a list, as AIP-158 specifies.
 *
 * <p>
 * The page has its items and the token for the next page. The token is blank on the last page. The total size
 * counts the full list if the engine counted it. An engine that does not count sets zero.
 */
public record Page<T>(List<T> items, String nextPageToken, int totalSize) {

  public Page {
    items = List.copyOf(items);
    nextPageToken = Objects.requireNonNullElse(nextPageToken, "");
  }
}
