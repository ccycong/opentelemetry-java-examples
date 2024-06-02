/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.example.http;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.ExtendedLongHistogramBuilder;
import io.opentelemetry.api.incubator.propagation.ExtendedContextPropagators;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.SemanticAttributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.LongCounter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

public final class HttpClient {

  // it's important to initialize the OpenTelemetry SDK as early in your applications lifecycle as
  // possible.
  private static final OpenTelemetry openTelemetry =
          ExampleConfiguration.initOpenTelemetryOtelExport();

  private static final Tracer tracer =
          openTelemetry.getTracer("io.opentelemetry.example.http.HttpClient");
  // 初始化一个 meter 指标
  // 如果已经看到 java项目的化可以看到
  // 不论是  tracing 还是 meter
  // 如果第三方库做了支持  比如 grpc 就可以很方便的输出相关数据,okhttp 应该也是有原生支持的
  // 如果使用原生http客户端和服务, 如下所示
  // 完全可以手动编码进行tracing 和 meter的初始化和数据生成
  private static final Meter meter = openTelemetry.getMeter("io.opentelemetry.example.http.HttpServer");
  // 初始化指标的一个直方图
  DoubleHistogramBuilder originalBuilder = meter.histogramBuilder("http.client.duration");
  ExtendedLongHistogramBuilder builder = (ExtendedLongHistogramBuilder) originalBuilder.ofLongs();
  static List<Long> bucketBoundaries = Arrays.asList(0L, 5L, 10L, 25L, 50L, 75L,100L);


  private void makeRequest() throws IOException, URISyntaxException {
      // 还不完全熟悉java语法 只能将 requestCount requestDuration 变量写在方法内
   LongHistogram requestDuration = builder.
            setDescription("The duration of HTTP requests").setUnit("ms").
            setExplicitBucketBoundariesAdvice(bucketBoundaries).build();

    final LongCounter requestCount = meter.counterBuilder("http.client.requests")
            .setDescription("The number of HTTP requests")
            .setUnit("1")
            .build();
    // 导出的指标如下
      // # HELP http_client_duration_milliseconds The duration of HTTP requests
      //# TYPE http_client_duration_milliseconds histogram
      //http_client_duration_milliseconds_bucket{http_method="GET",http_status_code="200",http_url="http://127.0.0.1:8080",job="OtelExporterExample  HTTP Client And Server",le="0"} 0
      //http_client_duration_milliseconds_bucket{http_method="GET",http_status_code="200",http_url="http://127.0.0.1:8080",job="OtelExporterExample  HTTP Client And Server",le="5"} 5
      //http_client_duration_milliseconds_bucket{http_method="GET",http_status_code="200",http_url="http://127.0.0.1:8080",job="OtelExporterExample  HTTP Client And Server",le="10"} 5
      //http_client_duration_milliseconds_bucket{http_method="GET",http_status_code="200",http_url="http://127.0.0.1:8080",job="OtelExporterExample  HTTP Client And Server",le="25"} 5
      //http_client_duration_milliseconds_bucket{http_method="GET",http_status_code="200",http_url="http://127.0.0.1:8080",job="OtelExporterExample  HTTP Client And Server",le="50"} 5
      //http_client_duration_milliseconds_bucket{http_method="GET",http_status_code="200",http_url="http://127.0.0.1:8080",job="OtelExporterExample  HTTP Client And Server",le="75"} 6
      //http_client_duration_milliseconds_bucket{http_method="GET",http_status_code="200",http_url="http://127.0.0.1:8080",job="OtelExporterExample  HTTP Client And Server",le="100"} 6
      //http_client_duration_milliseconds_bucket{http_method="GET",http_status_code="200",http_url="http://127.0.0.1:8080",job="OtelExporterExample  HTTP Client And Server",le="+Inf"} 6
      //http_client_duration_milliseconds_sum{http_method="GET",http_status_code="200",http_url="http://127.0.0.1:8080",job="OtelExporterExample  HTTP Client And Server"} 85
      //http_client_duration_milliseconds_count{http_method="GET",http_status_code="200",http_url="http://127.0.0.1:8080",job="OtelExporterExample  HTTP Client And Server"} 6
      //# HELP http_client_requests_total The number of HTTP requests
      //# TYPE http_client_requests_total counter
      //http_client_requests_total{job="OtelExporterExample  HTTP Client And Server"} 6
      //# HELP otlp_exporter_exported_total
      //# TYPE otlp_exporter_exported_total counter
      //otlp_exporter_exported_total{job="OtelExporterExample  HTTP Client And Server",success="true",type="span"} 6
      //# HELP otlp_exporter_seen_total
      //# TYPE otlp_exporter_seen_total counter
      //otlp_exporter_seen_total{job="OtelExporterExample  HTTP Client And Server",type="span"} 6
    int port = 8080;
    URL url = new URL("http://127.0.0.1:" + port);
    HttpURLConnection con = (HttpURLConnection) url.openConnection();

    int status = 0;
    StringBuilder content = new StringBuilder();

    // Name convention for the Span is not yet defined.
    // See: https://github.com/open-telemetry/opentelemetry-specification/issues/270
    Span span = tracer.spanBuilder("/").setSpanKind(SpanKind.CLIENT).startSpan();
    try (Scope scope = span.makeCurrent()) {
      span.setAttribute(SemanticAttributes.HTTP_REQUEST_METHOD, "GET1");
      span.setAttribute("component", "http");
      URI uri = url.toURI();
      url =
              new URI(
                      uri.getScheme(),
                      null,
                      uri.getHost(),
                      uri.getPort(),
                      uri.getPath(),
                      uri.getQuery(),
                      uri.getFragment())
                      .toURL();

      span.setAttribute(SemanticAttributes.URL_FULL, url.toString());

      // Inject the request with the current Context/Span.
      ExtendedContextPropagators.getTextMapPropagationContext(openTelemetry.getPropagators())
              .forEach(con::setRequestProperty);

      try {
        // Process the request
        long now = System.currentTimeMillis();
        con.setRequestMethod("GET");
        status = con.getResponseCode();
        // 指标如何被使用
        requestCount.add(1);
        // 直方图指标如何被使用
        requestDuration.record(System.currentTimeMillis() - now, Attributes.builder()
                .put("http.method", "GET")
                .put("http.status_code", Integer.toString(status))
                        .put("http.url", url.toString())
                .build());
        BufferedReader in =
                new BufferedReader(
                        new InputStreamReader(con.getInputStream(), Charset.defaultCharset()));
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
          content.append(inputLine);
        }
        in.close();
      } catch (Exception e) {
        span.setStatus(StatusCode.ERROR, "HTTP Code: " + status);
      }
    } finally {
      span.end();
    }

    // Output the result of the request
    System.out.println("Response Code: " + status);
    System.out.println("Response Msg: " + content);
  }

  public static void main(String[] args) {
    HttpClient httpClient = new HttpClient();

    // Perform request every 5s
    Thread t =
            new Thread(
                    () -> {
                      while (true) {
                        try {
                          httpClient.makeRequest();
                          Thread.sleep(5000);
                        } catch (Exception e) {
                          System.out.println(e.getMessage());
                        }
                      }
                    });
    t.start();
  }
}


