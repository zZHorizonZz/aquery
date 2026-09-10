package dev.horizon.aquery.aip158;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.horizon.aquery.InvalidQueryException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PageTokenCodecTest {

  private static final String PRINT = PageToken.fingerprintOf("username = \"dan\"", "create_time");

  private final PageTokenCodec codec = new PageTokenCodec(
      "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));

  @Test
  @DisplayName("carries the cursor back")
  void roundTrips() {
    PageToken token = new PageToken(List.of("dan", "2026-01-02T03:04:05Z", "42"), PRINT);

    assertThat(codec.decode(codec.encode(token), PRINT)).contains(token);
  }

  @Test
  @DisplayName("an absent token is the first page rather than an error")
  void firstPage() {
    assertThat(codec.decode(null, PRINT)).isEmpty();
    assertThat(codec.decode("", PRINT)).isEmpty();
    assertThat(codec.decode("   ", PRINT)).isEmpty();
  }

  @Test
  @DisplayName("keeps a value the encoding would otherwise eat")
  void awkwardValues() {
    PageToken token = new PageToken(List.of("a.b", "with space", "", "=", "ünïcøde", "%_"), PRINT);

    assertThat(codec.decode(codec.encode(token), PRINT).orElseThrow().cursor()).containsExactly("a.b", "with space", "", "=",
        "ünïcøde", "%_");
  }

  @Test
  @DisplayName("refuses a token issued for different arguments, naming the field")
  void boundToItsArguments() {
    String issued = codec.encode(new PageToken(List.of("dan"), PRINT));

    assertThatThrownBy(() -> codec.decode(issued, PageToken.fingerprintOf("username = \"eva\"", "create_time")))
        .isInstanceOf(InvalidPageTokenException.class)
        .hasMessageContaining("start the listing again")
        .satisfies(thrown -> assertThat(((InvalidQueryException) thrown).field()).isEqualTo(InvalidQueryException.PAGE_TOKEN));
  }

  @Test
  @DisplayName("refuses what it did not issue rather than reading it as a cursor")
  void refusesForeignTokens() {
    for (String foreign : List.of("not base64 at all!", "%%%", "ZGFu", "////", "AAAA", "AAAAAA")) {
      assertThatThrownBy(() -> codec.decode(foreign, PRINT))
          .as(foreign)
          .isInstanceOf(InvalidPageTokenException.class)
          .hasMessageContaining("not a token this service issued");
    }
  }

  @Test
  @DisplayName("refuses a token a client changed")
  void refusesTamperedTokens() {
    byte[] issued = Base64.getUrlDecoder().decode(codec.encode(new PageToken(List.of("42"), PRINT)));

    for (int i = 0; i < issued.length; i++) {
      byte[] tampered = Arrays.copyOf(issued, issued.length);
      tampered[i] ^= 1;
      String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tampered);
      assertThatThrownBy(() -> codec.decode(token, PRINT))
          .as("byte %d", i)
          .isInstanceOf(InvalidPageTokenException.class)
          .hasMessageContaining("not a token this service issued");
    }
  }

  @Test
  @DisplayName("refuses a token another key issued")
  void refusesOtherKeys() {
    PageTokenCodec other = new PageTokenCodec("fedcba9876543210".getBytes(StandardCharsets.US_ASCII));
    String issued = other.encode(new PageToken(List.of("42"), PRINT));

    assertThatThrownBy(() -> codec.decode(issued, PRINT)).isInstanceOf(InvalidPageTokenException.class);
  }

  @Test
  @DisplayName("is opaque: the client cannot read the cursor")
  void opaque() {
    String first = codec.encode(new PageToken(List.of("secret-cursor-value"), PRINT));
    String second = codec.encode(new PageToken(List.of("secret-cursor-value"), PRINT));

    assertThat(new String(Base64.getUrlDecoder().decode(first), StandardCharsets.ISO_8859_1)).doesNotContain("secret");
    assertThat(first).isNotEqualTo(second);
  }

  @Test
  @DisplayName("is safe to put in a URL")
  void urlSafe() {
    String encoded = codec.encode(new PageToken(List.of("a/b+c=d", "?&#"), PRINT));

    assertThat(encoded).doesNotContain("/", "+", "=", "?", "&", "#");
  }

  @Test
  @DisplayName("refuses keys that are not AES keys")
  void refusesBadKeys() {
    assertThatThrownBy(() -> new PageTokenCodec(new byte[10])).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("the fingerprint differs when the arguments do, and only then")
  void fingerprints() {
    assertThat(PageToken.fingerprintOf("a", "b")).isEqualTo(PageToken.fingerprintOf("a", "b"));
    assertThat(PageToken.fingerprintOf("a", "b")).isNotEqualTo(PageToken.fingerprintOf("b", "a"));

    assertThat(PageToken.fingerprintOf("ab", "")).isNotEqualTo(PageToken.fingerprintOf("a", "b"));
    assertThat(PageToken.fingerprintOf("a\0", "b")).isNotEqualTo(PageToken.fingerprintOf("a", "\0b"));
    assertThat(PageToken.fingerprintOf((String) null)).isEqualTo(PageToken.fingerprintOf(""));
  }
}
