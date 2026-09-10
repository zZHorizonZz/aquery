package dev.horizon.aquery.aip158;

import static java.util.stream.Collectors.joining;

import dev.horizon.aquery.aip132.OrderBy;
import dev.horizon.aquery.aip160.Filter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * The contents of a page token, as AIP-158 specifies: a cursor and a fingerprint.
 *
 * <p>
 * The cursor contains the last row of the page. It has one value for each term of the keyset. The next page
 * starts after that row. The fingerprint tells which list the token belongs to. Thus a different list cannot use
 * the token.
 *
 * <p>
 * Use {@link PageTokenCodec} to change a token into text and back. The codec encrypts the token and makes sure
 * that nobody changed it. The client cannot read or change the text.
 *
 * <p>
 * The fingerprint protects the continuation. The server makes a list from arguments: the filter and the order.
 * The codec refuses a token from one set of arguments for a different set. A page that continues at a row of a
 * different list has no meaning. If the client changes the arguments, the client must start the list again.
 *
 * <p>
 * Make the fingerprint with {@link #fingerprintOf} from all arguments of the list. Do not include the page size.
 * AIP-158 says that the client can change the page size between pages. Equal arguments give equal fingerprints.
 * Different arguments give different fingerprints.
 *
 * <p>
 * A page token does not give permission to read data. Do the authorization of each request as usual.
 */
public record PageToken(List<String> cursor, String fingerprint) {

  public PageToken {
    cursor = List.copyOf(cursor);
    Objects.requireNonNull(fingerprint, "fingerprint");
  }

  /**
   * Gives the fingerprint of the arguments of a list.
   *
   * <p>
   * Equal arguments give equal fingerprints, and different arguments give different fingerprints. A null argument is
   * equal to an empty string.
   *
   * @param arguments the arguments of the list, in a fixed sequence
   * @return the fingerprint as 16 hexadecimal digits
   */
  public static String fingerprintOf(String... arguments) {
    MessageDigest digest = sha256();
    for (String argument : arguments) {
      byte[] bytes = Objects.requireNonNullElse(argument, "").getBytes(StandardCharsets.UTF_8);
      // The length before each argument keeps ("ab", "") different from ("a", "b").
      digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
      digest.update(bytes);
    }
    return HexFormat.of().formatHex(digest.digest(), 0, 8);
  }

  /**
   * Gives the fingerprint of a parsed filter, a parsed order and other arguments of the list.
   *
   * <p>
   * The fingerprint uses the parsed forms. Thus {@code a="x"} and {@code a = "x"} give the same fingerprint. Add
   * the other arguments that change the list, for example the parent resource.
   *
   * @param filter the parsed filter, or null if the list has no filter
   * @param order the order of the list, usually the merged order
   * @param scope the other arguments of the list, in a fixed sequence
   * @return the fingerprint as 16 hexadecimal digits
   */
  public static String fingerprintOf(Filter filter, List<OrderBy> order, String... scope) {
    String[] arguments = new String[scope.length + 2];
    arguments[0] = String.valueOf(filter == null ? new Filter(null) : filter);
    arguments[1] = order.stream().map(term -> term.fieldPath() + (term.descending() ? " desc" : "")).collect(joining(","));
    System.arraycopy(scope, 0, arguments, 2, scope.length);
    return fingerprintOf(arguments);
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
    }
  }
}
