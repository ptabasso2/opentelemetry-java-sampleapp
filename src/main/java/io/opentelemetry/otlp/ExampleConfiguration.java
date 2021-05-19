/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.otlp;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * All SDK management takes place here, away from the instrumentation code, which should only access
 * the OpenTelemetry APIs.
 */
public final class ExampleConfiguration {

  /**
   * Adds a BatchSpanProcessor initialized with OtlpGrpcSpanExporter to the TracerSdkProvider.
   *
   * @return a ready-to-use {@link OpenTelemetry} instance.
   */
  static OpenTelemetry initOpenTelemetry() {


    OtlpGrpcSpanExporter spanExporter =
        OtlpGrpcSpanExporter.builder().setTimeout(25, TimeUnit.SECONDS).build();

    BatchSpanProcessor spanProcessor =
        BatchSpanProcessor.builder(spanExporter)
            .setScheduleDelay(10000, TimeUnit.MILLISECONDS)
            .build();


    /*
    SimpleSpanProcessor spanProcessor =
        (SimpleSpanProcessor) SimpleSpanProcessor.create(OtlpGrpcSpanExporter.builder().setTimeout(25, TimeUnit.SECONDS).build());
    */

    SdkTracerProvider sdkTracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(spanProcessor)
            .setResource(OpenTelemetrySdkAutoConfiguration.getResource())
            .build();


    OpenTelemetrySdk openTelemetrySdk =
        OpenTelemetrySdk.builder().setTracerProvider(sdkTracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance())).buildAndRegisterGlobal();

    Runtime.getRuntime().addShutdownHook(new Thread(sdkTracerProvider::close));

    return openTelemetrySdk;
  }

  /**
   * Initializes a Metrics SDK with a OtlpGrpcMetricExporter and an IntervalMetricReader.
   *
   * @return a ready-to-use {@link MeterProvider} instance
   */
  static MeterProvider initOpenTelemetryMetrics() {
    // set up the metric exporter and wire it into the SDK and a timed reader.
    OtlpGrpcMetricExporter metricExporter = OtlpGrpcMetricExporter.getDefault();

    SdkMeterProvider meterProvider = SdkMeterProvider.builder().buildAndRegisterGlobal();
    IntervalMetricReader intervalMetricReader =
        IntervalMetricReader.builder()
            .setMetricExporter(metricExporter)
            .setMetricProducers(Collections.singleton(meterProvider))
            .setExportIntervalMillis(1000)
            .build();

    Runtime.getRuntime().addShutdownHook(new Thread(intervalMetricReader::shutdown));

    return meterProvider;
  }
}
