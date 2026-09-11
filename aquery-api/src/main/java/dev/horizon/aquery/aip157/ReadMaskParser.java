package dev.horizon.aquery.aip157;

/**
 * Parses the text of an AIP-157 read mask.
 *
 * <p>
 * This interface connects the api to the implementation. The api module reads the implementation from the module
 * path or the class path at runtime. The internal module supplies it.
 *
 * @see ReadMask#parse
 */
public interface ReadMaskParser {

  /**
   * Parses a read mask.
   *
   * @param readMask the text from the client. Null or blank text means all fields
   * @return the parsed read mask
   * @throws InvalidReadMaskException if the text does not follow the read mask grammar
   */
  ReadMask parse(String readMask);
}
