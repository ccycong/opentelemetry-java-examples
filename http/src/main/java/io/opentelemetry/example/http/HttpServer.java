/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.example.http;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.trace.ExtendedTracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.stream.Collectors;

public final class HttpServer {
  // It's important to initialize your OpenTelemetry SDK as early in your application's lifecycle as
  // possible.
  private static final OpenTelemetry openTelemetry = ExampleConfiguration.initOpenTelemetryOtelExport();
  private static final ExtendedTracer tracer =
          ExtendedTracer.create(openTelemetry.getTracer("io.opentelemetry.example.http.HttpServer"));

  private static final int port = 8080;
  private final com.sun.net.httpserver.HttpServer server;

  private HttpServer() throws IOException {
    this(port);
  }

  private HttpServer(int port) throws IOException {
    server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 0);
    // Test urls
    server.createContext("/", new HelloHandler());
    server.start();
    System.out.println("Server ready on http://127.0.0.1:" + port);
  }

  private static class HelloHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      Context context = openTelemetry.getPropagators().getTextMapPropagator().extract(Context.current(), exchange, new TextMapGetter<HttpExchange>() {
        @Override
        public Iterable<String> keys(HttpExchange carrier) {
          return carrier.getRequestHeaders().keySet();
        }

        @Override
        public String get(HttpExchange carrier, String key) {
          return carrier.getRequestHeaders().getFirst(key);
        }
      });
      // .setParentFrom(
      //              openTelemetry.getPropagators(),
      //              exchange.getRequestHeaders().entrySet().stream()
      //                  .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0)))) 存在无法获取 tracing id的问题
      // 换用 Context context = openTelemetry.getPropagators().getTextMapPropagator().extract(Context.current(), exchange, new TextMapGetter<HttpExchange>() {
      //  以及  .setParent(context)
      tracer
              .spanBuilder("GET /")

              .setSpanKind(SpanKind.SERVER)
              .startAndRun(
                      () -> {
                        // Set the Semantic Convention
                        Span span = Span.current();
                        // Dump查看 tracing 如何跨服务
                        dumpRequestDetails(exchange, span);
                        // stdout查看 tracing 如何跨服务
                        System.out.println("server span trace ID " + span.getSpanContext().getTraceId());
                        System.out.println("server span span ID " + span.getSpanContext().getSpanId());

                        span.setAttribute("component", "http");
                        span.setAttribute("http.method", "GET");
                /*
                 One of the following is required:
                 - http.scheme, http.host, http.target
                 - http.scheme, http.server_name, net.host.port, http.target
                 - http.scheme, net.host.name, net.host.port, http.target
                 - http.url
                */
                        span.setAttribute("http.scheme", "http");
                        span.setAttribute("http.host", "localhost:" + HttpServer.port);
                        span.setAttribute("http.target", "/");

                        // Process the request
                        answer(exchange, span);
                      });
    }
    // Dump查看 tracing 如何跨服务
    private void dumpRequestDetails(HttpExchange exchange, Span span) {
      StringBuilder dump = new StringBuilder();

      // Request method and URI
      dump.append("Request Method: ").append(exchange.getRequestMethod()).append("\n");
      dump.append("Request URI: ").append(exchange.getRequestURI()).append("\n");

      // Request Headers
      dump.append("Request Headers:\n");
      exchange.getRequestHeaders().forEach((key, value) -> {
        dump.append(key).append(": ").append(String.join(", ", value)).append("\n");
      });

      // Log the dump to console (or you can add it as an event to the span)
      System.out.println(dump.toString());

      // Optionally, add this information as an event to the span
      span.addEvent("HTTP Request Dump", Attributes.of(stringKey("dump"), dump.toString()));
    }

    private void dumpRequestDetailsWithoutSpan(HttpExchange exchange) {
      StringBuilder dump = new StringBuilder();

      // Request method and URI
      dump.append("Request Method: ").append(exchange.getRequestMethod()).append("\n");
      dump.append("Request URI: ").append(exchange.getRequestURI()).append("\n");

      // Request Headers
      dump.append("Request Headers:\n");
      exchange.getRequestHeaders().forEach((key, value) -> {
        dump.append(key).append(": ").append(String.join(", ", value)).append("\n");
      });

      // Log the dump to console (or you can add it as an event to the span)
      System.out.println(dump.toString());
    }
    private void answer(HttpExchange exchange, Span span) throws IOException {
      // Generate an Event
      span.addEvent("Start Processing");

      // Process the request
      String response = "Hello World!";
      exchange.sendResponseHeaders(200, response.length());
      OutputStream os = exchange.getResponseBody();
      os.write(response.getBytes(Charset.defaultCharset()));
      os.close();
      System.out.println("Served Client: " + exchange.getRemoteAddress());

      // Generate an Event with an attribute
      Attributes eventAttributes = Attributes.of(stringKey("answer"), response);
      span.addEvent("Finish Processing", eventAttributes);
    }
  }

  private void stop() {
    server.stop(0);
  }

  /**
   * Main method to run the example.
   *
   * @param args It is not required.
   * @throws Exception Something might go wrong.
   */
  public static void main(String[] args) throws Exception {
    final HttpServer s = new HttpServer();
    // Gracefully close the server
    Runtime.getRuntime().addShutdownHook(new Thread(s::stop));
  }
}
