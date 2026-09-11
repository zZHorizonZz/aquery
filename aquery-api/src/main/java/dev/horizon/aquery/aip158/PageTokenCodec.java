package dev.horizon.aquery.aip158;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Changes a {@link PageToken} into text for the client, and changes the text back.
 *
 * <p>
 * AIP-158 says that a page token must be opaque. The client must not read it. Base64 of readable data is not
 * sufficient. Thus the codec encrypts the token with AES-GCM and a key of the server. AES-GCM also authenticates
 * the token. The codec refuses a token that a client changed or made. Thus the cursor values come only from the
 * server.
 *
 * <p>
 * The text is unpadded base64url. A URL can contain it. The format of the text is not part of the contract.
 *
 * <p>
 * Give the codec a secret AES key of 128, 192 or 256 bits. Keep the key secret. All servers of one API must use
 * the same key. If you change the key, the servers refuse the tokens of the old key, and the clients must start
 * their lists again. The constructors throw {@link IllegalArgumentException} for a key that is not an AES key.
 *
 * <p>
 * A codec is safe for use by several threads at the same time.
 */
public final class PageTokenCodec {

  private static final byte FORMAT = 2;
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final int MAX_CURSOR_TERMS = 1024;
  private static final String CIPHER = "AES/GCM/NoPadding";
  private static final String FOREIGN = "page_token is not a token this service issued";

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  private final SecretKey key;

  public PageTokenCodec(SecretKey key) {
    Objects.requireNonNull(key, "key");
    if (!"AES".equalsIgnoreCase(key.getAlgorithm())) {
      throw new IllegalArgumentException("page tokens need an AES key, was " + key.getAlgorithm());
    }
    this.key = key;
  }

  public PageTokenCodec(byte[] key) {
    this(new SecretKeySpec(requireAesLength(key), "AES"));
  }

  /**
   * Encrypts the token into text for the client.
   *
   * <p>
   * Two calls with the same token give different text.
   *
   * @param token the token of the next page
   * @return the encrypted token as unpadded base64url text
   */
  public String encode(PageToken token) {
    byte[] iv = new byte[IV_BYTES];
    RANDOM.nextBytes(iv);
    try {
      Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv);
      byte[] sealed = cipher.doFinal(serialize(token));
      return ENCODER.encodeToString(ByteBuffer.allocate(1 + IV_BYTES + sealed.length).put(FORMAT).put(iv).put(sealed).array());
    } catch (IllegalBlockSizeException | BadPaddingException impossible) {
      throw new IllegalStateException("AES-GCM refused to encrypt a page token", impossible);
    }
  }

  /**
   * Reads a token that the client sent.
   *
   * @param token the text from the client. Blank text means the first page, not an error
   * @param fingerprint the fingerprint of the list that the token must belong to
   * @return the token, or empty if the client asked for the first page
   * @throws InvalidPageTokenException if this service did not make the token, if a client changed it, or if it belongs to a
   * different list
   */
  public Optional<PageToken> decode(String token, String fingerprint) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }

    PageToken decoded;
    try {
      byte[] bytes = DECODER.decode(token);
      if (bytes.length < 1 + IV_BYTES + TAG_BITS / Byte.SIZE || bytes[0] != FORMAT) {
        throw new InvalidPageTokenException(FOREIGN);
      }
      Cipher cipher = cipher(Cipher.DECRYPT_MODE, Arrays.copyOfRange(bytes, 1, 1 + IV_BYTES));
      decoded = deserialize(cipher.doFinal(bytes, 1 + IV_BYTES, bytes.length - 1 - IV_BYTES));
    } catch (IllegalArgumentException | IllegalBlockSizeException | BadPaddingException unreadable) {
      // A failed authentication tag is a BadPaddingException. A client changed or made the token.
      throw new InvalidPageTokenException(FOREIGN);
    }

    if (!decoded.fingerprint().equals(fingerprint)) {
      throw new InvalidPageTokenException("page_token was issued for different arguments; start the listing again");
    }
    return Optional.of(decoded);
  }

  private Cipher cipher(int mode, byte[] iv) {
    try {
      Cipher cipher = Cipher.getInstance(CIPHER);
      cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
      // The format byte is authenticated data. Thus a client cannot change it.
      cipher.updateAAD(new byte[] { FORMAT });
      return cipher;
    } catch (GeneralSecurityException misconfigured) {
      throw new IllegalStateException("the page token key cannot be used with " + CIPHER, misconfigured);
    }
  }

  private static byte[] serialize(PageToken token) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    writeVarint(bytes, token.cursor().size());
    token.cursor().forEach(value -> writeString(bytes, value));
    writeString(bytes, token.fingerprint());
    return bytes.toByteArray();
  }

  private static PageToken deserialize(byte[] bytes) {
    Reader reader = new Reader(bytes);
    int size = reader.varint();
    if (size < 0 || size > MAX_CURSOR_TERMS) {
      throw new IllegalArgumentException("the cursor has " + size + " terms");
    }
    List<String> cursor = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      cursor.add(reader.string());
    }
    String fingerprint = reader.string();
    if (reader.remaining() != 0) {
      throw new IllegalArgumentException("the token has extra bytes");
    }
    return new PageToken(cursor, fingerprint);
  }

  private static void writeString(ByteArrayOutputStream bytes, String value) {
    byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
    writeVarint(bytes, utf8.length);
    bytes.writeBytes(utf8);
  }

  private static void writeVarint(ByteArrayOutputStream bytes, int value) {
    while (value >= 0x80) {
      bytes.write((value & 0x7F) | 0x80);
      value >>>= 7;
    }
    bytes.write(value);
  }

  private static byte[] requireAesLength(byte[] key) {
    Objects.requireNonNull(key, "key");
    if (key.length != 16 && key.length != 24 && key.length != 32) {
      throw new IllegalArgumentException("page token keys have 16, 24 or 32 bytes, was " + key.length);
    }
    return key;
  }

  private static final class Reader {

    private final byte[] bytes;
    private int position;

    private Reader(byte[] bytes) {
      this.bytes = bytes;
    }

    private int remaining() {
      return bytes.length - position;
    }

    private int varint() {
      int result = 0;
      for (int shift = 0; shift < 35; shift += 7) {
        if (remaining() < 1) {
          throw new IllegalArgumentException("the token ended early");
        }
        int b = bytes[position++] & 0xFF;
        result |= (b & 0x7F) << shift;
        if ((b & 0x80) == 0) {
          return result;
        }
      }
      throw new IllegalArgumentException("a varint is too long");
    }

    private String string() {
      int length = varint();
      if (length < 0 || length > remaining()) {
        throw new IllegalArgumentException("a string does not fit the token");
      }
      String value = new String(bytes, position, length, StandardCharsets.UTF_8);
      position += length;
      return value;
    }
  }
}
