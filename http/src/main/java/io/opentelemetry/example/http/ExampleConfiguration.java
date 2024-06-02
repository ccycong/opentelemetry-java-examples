/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.example.http;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import static io.opentelemetry.semconv.ResourceAttributes.SERVICE_NAME;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;

import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

// 参考 otel examples grpc

class ExampleConfiguration {
  static OpenTelemetry initOpenTelemetryStdLoggingExporter() {
    Resource resource =
            Resource.getDefault()
                    .merge(Resource.builder().put(SERVICE_NAME, "OtlpExporterExample HTTP").build());

    SdkTracerProvider sdkTracerProvider =
            SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(new LoggingSpanExporter()))
                    .addResource(resource)
                    .build();

    SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder()
            .setResource(resource)
            .registerMetricReader(
                    PeriodicMetricReader.builder(new LoggingMetricExporter())
                            .setInterval(Duration.ofMillis(1000))
                            .build()).build();

    OpenTelemetrySdk sdk =
            OpenTelemetrySdk.builder()
                    .setTracerProvider(sdkTracerProvider)
                    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance())).setMeterProvider(
                            sdkMeterProvider
                    )
                    .build();

    Runtime.getRuntime().addShutdownHook(new Thread(sdkTracerProvider::close));
    return sdk;
  }
  static OpenTelemetry initOpenTelemetryOtelExport() {
    // Include required service.name resource attribute on all spans and metrics
    Resource resource =
            Resource.getDefault()
                    // public static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
                    .merge(Resource.builder().put(SERVICE_NAME, "OtelExporterExample  HTTP Client And Server").build());

    SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder()
            .setResource(resource)
            .registerMetricReader(
                    PeriodicMetricReader.builder(OtlpGrpcMetricExporter.builder().setEndpoint("http://10.6.35.168:30017").build())
                            .setInterval(Duration.ofMillis(1000))
                            .build()).build();

    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(
                    BatchSpanProcessor.builder(
                                    OtlpGrpcSpanExporter.builder()
                                            .setTimeout(2, TimeUnit.SECONDS)
                                            .setEndpoint("http://10.6.35.168:30017")
                                            .build())
                            .setScheduleDelay(100, TimeUnit.MILLISECONDS)
                            .build())
            .build();

    OpenTelemetrySdk openTelemetrySdk =
            OpenTelemetrySdk.builder()
                    .setTracerProvider(sdkTracerProvider).
                    setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance())).
                    setMeterProvider(sdkMeterProvider).
                    buildAndRegisterGlobal();

    Runtime.getRuntime().addShutdownHook(new Thread(openTelemetrySdk::close));

    return openTelemetrySdk;
  }
}
