module dev.horizon.aquery.internal {
  requires dev.horizon.aquery.api;

  provides dev.horizon.aquery.aip132.OrderByParser with
      dev.horizon.aquery.internal.aip132.InternalOrderByParser;

  provides dev.horizon.aquery.aip157.ReadMaskParser with
      dev.horizon.aquery.internal.aip157.InternalReadMaskParser;

  provides dev.horizon.aquery.aip160.FilterParser with
      dev.horizon.aquery.internal.aip160.InternalFilterParser;
}
