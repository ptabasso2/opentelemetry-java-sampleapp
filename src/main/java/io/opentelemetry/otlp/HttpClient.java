/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.otlp;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;

/**
 * Example code for setting up the OTLP exporters.
 *
 * <p>If you wish to use this code, you'll need to run a copy of the collector locally, on the
 * default port. There is a docker-compose configuration for doing this in the docker subdirectory
 * of this module.
 */
public final class HttpClient {

  /*
  private final Map<String, String> carrier = new HashMap<>();

  private static final TextMapSetter<Map<String, String>> setter =
      (carrier, key, value) -> carrier.put(key, value);

  private final TextMapSetter<Map<String, String>> setter2 = Map::put;
  */

  public static void main(String[] args) throws InterruptedException {

    System.setProperty("otel.resource.attributes", "service.name=HttpClient");

    OpenTelemetry openTelemetry = ExampleConfiguration.initOpenTelemetry();
    Tracer tracer =
        openTelemetry.getTracer("io.opentelemetry.example.http.HttpClient");
    TextMapPropagator textMapPropagator =
        openTelemetry.getPropagators().getTextMapPropagator();

     TextMapSetter<HttpURLConnection> setter = URLConnection::setRequestProperty;


    /*TextMapSetter<HttpURLConnection> setter = new TextMapSetter<HttpURLConnection>() {
      @Override
      public void set(@Nullable HttpURLConnection carrier, String key, String value) {
        carrier.setRequestProperty(key, value);
      }
    };*/


    // this will make sure that a proper service.name attribute is set on all the spans/metrics.
    // note: this is not something you should generally do in code, but should be provided on the
    // command-line. This is here to make the example more self-contained.

    // it is important to initialize your SDK as early as possible in your application's lifecycle
    ////OpenTelemetry openTelemetry = PejExampleConfiguration.initOpenTelemetry();
    // note: currently metrics is alpha and the configuration story is still unfolding. This will
    // definitely change in the future.
    MeterProvider meterProvider = ExampleConfiguration.initOpenTelemetryMetrics();

    ////Tracer tracer = openTelemetry.getTracer("io.opentelemetry.example");

    Meter meter = meterProvider.get("io.opentelemetry.example");
    LongCounter counter = meter.longCounterBuilder("example_counter").build();

/*
    Span exampleSpan = tracer.spanBuilder("exampleSpan").startSpan();
    try (Scope scope = exampleSpan.makeCurrent()) {
      counter.add(1);
      exampleSpan.setAttribute("good", "true");
      exampleSpan.setAttribute("exampleNumber", 10);
      Thread.sleep(100);
    } finally {
      exampleSpan.end();
    }
*/

    int port = 8080;
    URL url = null;
    try {
      url = new URL("http://127.0.0.1:" + port);
    } catch (MalformedURLException e) {
      e.printStackTrace();
    }
    HttpURLConnection con = null;
    try {
      con = (HttpURLConnection) url.openConnection();
    } catch (IOException e) {
      e.printStackTrace();
    }

    int status = 0;
    StringBuilder content = new StringBuilder();

    // Name convention for the Span is not yet defined.
    // See: https://github.com/open-telemetry/opentelemetry-specification/issues/270
    Span span = tracer.spanBuilder("/").setSpanKind(SpanKind.CLIENT).startSpan();
    try (Scope scope = span.makeCurrent()) {
      span.setAttribute(SemanticAttributes.HTTP_METHOD, "GET");
      span.setAttribute("component", "http");
      /*
       Only one of the following is required:
         - http.url
         - http.scheme, http.host, http.target
         - http.scheme, peer.hostname, peer.port, http.target
         - http.scheme, peer.ip, peer.port, http.target
      */
      span.setAttribute(SemanticAttributes.HTTP_URL, url.toString());

      // Inject the request with the current Context/Span.
      textMapPropagator.inject(Context.current(), con, setter);

      try {
        // Process the request
        con.setRequestMethod("GET");
        status = con.getResponseCode();
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



    /*
    for (int i = 0; i < 10; i++) {
      Span exampleSpan = tracer.spanBuilder("exampleSpan").startSpan();
      try (Scope scope = exampleSpan.makeCurrent()) {
        counter.add(1);
        exampleSpan.setAttribute("good", "true");
        exampleSpan.setAttribute("exampleNumber", i);
        Thread.sleep(100);
      } finally {
        exampleSpan.end();
      }
    }
    */

    // sleep for a bit to let everything settle
    Thread.sleep(2000);
  }
}
