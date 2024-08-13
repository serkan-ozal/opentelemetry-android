/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.library.okhttp.v3_0.internal;

import static java.util.Collections.singletonList;

import static io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor.alwaysInternal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientRequestResendCount;
import io.opentelemetry.instrumentation.library.okhttp.v3_0.OkHttpInstrumentationConfig;
import io.opentelemetry.instrumentation.okhttp.v3_0.internal.ConnectionErrorSpanInterceptor;
import io.opentelemetry.instrumentation.okhttp.v3_0.internal.OkHttpAttributesGetter;
import io.opentelemetry.instrumentation.okhttp.v3_0.internal.OkHttpInstrumenterFactory;
import io.opentelemetry.instrumentation.okhttp.v3_0.internal.TracingInterceptor;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class OkHttp3Singletons {

    private static final String INSTRUMENTATION_NAME = "io.opentelemetry.okhttp-3.0";

    private static final Supplier<Instrumenter<Request, Response>> INSTRUMENTER =
            CachedSupplier.create(
                    () ->
                            OkHttpInstrumenterFactory.create(
                                    GlobalOpenTelemetry.get(),
                                    builder ->
                                            builder.setCapturedRequestHeaders(
                                                            OkHttpInstrumentationConfig
                                                                    .getCapturedRequestHeaders())
                                                    .setCapturedResponseHeaders(
                                                            OkHttpInstrumentationConfig
                                                                    .getCapturedResponseHeaders())
                                                    .setKnownMethods(
                                                            OkHttpInstrumentationConfig
                                                                    .getKnownMethods()),
                                    spanNameExtractorConfigurer ->
                                            spanNameExtractorConfigurer.setKnownMethods(
                                                    OkHttpInstrumentationConfig.getKnownMethods()),
                                    Function.identity(),
                                    singletonList(
                                            PeerServiceAttributesExtractor.create(
                                                    OkHttpAttributesGetter.INSTANCE,
                                                    OkHttpInstrumentationConfig
                                                            .newPeerServiceResolver())),
                                    OkHttpInstrumentationConfig
                                            .emitExperimentalHttpClientMetrics()));

    private static final Supplier<Instrumenter<OkHttpEvent, Void>> EVENT_INSTRUMENTER =
            CachedSupplier.create(
                    () ->
                            Instrumenter.<OkHttpEvent, Void>builder(
                                            GlobalOpenTelemetry.get(),
                                            INSTRUMENTATION_NAME,
                                            OkHttpEvent::getName)
                                    .buildInstrumenter(alwaysInternal()));

    public static final Interceptor CALLBACK_CONTEXT_INTERCEPTOR =
            chain -> {
                Request request = chain.request();
                Context context =
                        OkHttpCallbackAdviceHelper.tryRecoverPropagatedContextFromCallback(request);
                if (context != null) {
                    try (Scope ignored = context.makeCurrent()) {
                        return chain.proceed(request);
                    }
                }

                return chain.proceed(request);
            };

    public static final Interceptor RESEND_COUNT_CONTEXT_INTERCEPTOR =
            chain -> {
                try (Scope ignored =
                        HttpClientRequestResendCount.initialize(Context.current()).makeCurrent()) {
                    return chain.proceed(chain.request());
                }
            };

    public static final Interceptor CONNECTION_ERROR_INTERCEPTOR =
            new LazyInterceptor<>(
                    CachedSupplier.create(
                            () -> new ConnectionErrorSpanInterceptor(INSTRUMENTER.get())));

    public static final Interceptor TRACING_INTERCEPTOR =
            new LazyInterceptor<>(
                    CachedSupplier.create(
                            () ->
                                    new TracingInterceptor(
                                            INSTRUMENTER.get(),
                                            GlobalOpenTelemetry.getPropagators())));

    public static final EventListener.Factory TRACING_EVENT_LISTENER_FACTORY =
            new EventListener.Factory() {
                final AtomicLong nextCallId = new AtomicLong(1L);

                @Override
                public EventListener create(Call call) {
                    long callId = nextCallId.getAndIncrement();
                    return new TracingEventListener(EVENT_INSTRUMENTER.get(), callId, System.nanoTime());
                }
            };

    private OkHttp3Singletons() {}
}
