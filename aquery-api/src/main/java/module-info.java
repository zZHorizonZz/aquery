module dev.horizon.aquery.api {
  exports dev.horizon.aquery;
  exports dev.horizon.aquery.aip132;
  exports dev.horizon.aquery.aip157;
  exports dev.horizon.aquery.aip158;
  exports dev.horizon.aquery.aip160;
  exports dev.horizon.aquery.common to dev.horizon.aquery.internal;

  uses dev.horizon.aquery.aip132.OrderByParser;
  uses dev.horizon.aquery.aip157.ReadMaskParser;
}
