package io.opentelemetry.examples.grpc;/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

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

public final class ExampleConfiguration  {
  static OpenTelemetry initOpenTelemetryStdLoggingExporter() {
    // note(ccy) 需要添加一些全局的资源标识    比如操作系统名,本服务名...
    //  opentelemetry-semconv-1.25.0-alpha.jar!\io\opentelemetry\semconv\ResourceAttributes.class
    Resource resource =
            Resource.getDefault()
                    .merge(Resource.builder().put(SERVICE_NAME, "OtlpExporterExample GRPC").build());
    // note(ccy) otel Provider 数据产生后如何提供出去
    //  new LoggingSpanExporter() 将所有信息输出到控制台 便于理解 otel tracing 数据产生及如何导出
    SdkTracerProvider sdkTracerProvider =
            SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(new LoggingSpanExporter()))
                    .addResource(resource)
                    .build();
  // note(ccy) // note(ccy) otel Provider 数据产生后如何提供出去
  //  new LoggingMetricExporter() 将所有信息输出到控制台 便于理解 otel metric 数据产生及如何导出
    SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder()
            .setResource(resource)
            .registerMetricReader(
                    PeriodicMetricReader.builder(new LoggingMetricExporter())
                            .setInterval(Duration.ofMillis(1000))
                            .build()).build();
    // 使用初始化的对象
    OpenTelemetrySdk sdk =
            OpenTelemetrySdk.builder()
                    .setTracerProvider(sdkTracerProvider)
                    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance())).setMeterProvider(
                            sdkMeterProvider
                    )
                    .build();
    // 必要的步骤  在某些进程退出情况下 可以进行下面的调用 比如 kill 15
    Runtime.getRuntime().addShutdownHook(new Thread(sdkTracerProvider::close));
    return sdk;
  }
  static OpenTelemetry initOpenTelemetryOtelExport() {
    Resource resource =
            Resource.getDefault()
                    // public static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
                    .merge(Resource.builder().put(SERVICE_NAME, "OtelExporterExample GRPC Client And Server").build());

    SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder()
            .setResource(resource)
            .registerMetricReader(
                    // initOpenTelemetryStdLoggingExporter 中直接导出到控制台
                    // OtlpGrpcMetricExporter 导出到  otel collector receiver 或者一切符合 otel grpc receiver标准的endpoint
                    PeriodicMetricReader.builder(OtlpGrpcMetricExporter.builder().setEndpoint("http://10.6.35.168:30017").build())
                            .setInterval(Duration.ofMillis(1000))
                            .build()).build();

    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(
                    BatchSpanProcessor.builder(
                                    // initOpenTelemetryStdLoggingExporter 中直接导出到控制台
                                    // OtlpGrpcMetricExporter 导出到  otel collector receiver 或者一切符合 otel grpc receiver标准的endpoint
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
