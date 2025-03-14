/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.library.okhttp.v3_0.internal;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientRequestResendCount;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.library.okhttp.v3_0.OkHttpInstrumentation;
import io.opentelemetry.instrumentation.okhttp.v3_0.internal.ConnectionErrorSpanInterceptor;
import io.opentelemetry.instrumentation.okhttp.v3_0.internal.OkHttpAttributesGetter;
import io.opentelemetry.instrumentation.okhttp.v3_0.internal.OkHttpClientInstrumenterBuilderFactory;
import io.opentelemetry.instrumentation.okhttp.v3_0.internal.TracingInterceptor;
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
    private static final Interceptor NOOP_INTERCEPTOR = (chain -> chain.proceed(chain.request()));
    public static Interceptor CONNECTION_ERROR_INTERCEPTOR = NOOP_INTERCEPTOR;
    public static Interceptor TRACING_INTERCEPTOR = NOOP_INTERCEPTOR;
    public static EventListener.Factory TRACING_EVENT_LISTENER_FACTORY = null;

    public static void configure(
            OkHttpInstrumentation instrumentation, OpenTelemetry openTelemetry) {
        Instrumenter<Interceptor.Chain, Response> instrumenter =
                OkHttpClientInstrumenterBuilderFactory.create(openTelemetry)
                        .setCapturedRequestHeaders(instrumentation.getCapturedRequestHeaders())
                        .setCapturedResponseHeaders(instrumentation.getCapturedResponseHeaders())
                        .setKnownMethods(instrumentation.getKnownMethods())
                        // TODO: Do we really need to set the known methods on the span
                        // name
                        // extractor as well?
                        .setSpanNameExtractor(
                                x ->
                                        HttpSpanNameExtractor.builder(
                                                        OkHttpAttributesGetter.INSTANCE)
                                                .setKnownMethods(instrumentation.getKnownMethods())
                                                .build())
                        .addAttributeExtractor(
                                PeerServiceAttributesExtractor.create(
                                        OkHttpAttributesGetter.INSTANCE,
                                        instrumentation.newPeerServiceResolver()))
                        .setEmitExperimentalHttpClientMetrics(
                                instrumentation.emitExperimentalHttpClientMetrics())
                        .build();
        CONNECTION_ERROR_INTERCEPTOR =
                new CallAwareTracingInterceptor(
                    new ConnectionErrorSpanInterceptor(instrumenter)
                );
        TRACING_INTERCEPTOR =
                new CallAwareTracingInterceptor(
                    new TracingInterceptor(instrumenter, openTelemetry.getPropagators())
                );

        Tracer tracer = openTelemetry.getTracer("io.opentelemetry.okhttp-3.0");
        TRACING_EVENT_LISTENER_FACTORY = new EventListener.Factory() {
            final AtomicLong nextCallId = new AtomicLong(1L);
            @Override
            public EventListener create(Call call) {
                long callId = nextCallId.getAndIncrement();
                return new TracingEventListener(tracer, callId, System.nanoTime());
            }
        };
    }

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
                Call call = chain.call();
                Context existingCallContext =
                        OkHttpCallAdviceHelper.tryRecoverPropagatedContextFromCall(call);
                Consumer<Throwable> errorCallback =
                        OkHttpCallAdviceHelper.tryRecoverErrorCallbackFromCall(call);
                Context callContext = existingCallContext;
                if (callContext == null) {
                    callContext = Context.current();
                }
                Context newCallContext = HttpClientRequestResendCount.initialize(callContext);
                try (Scope ignored = newCallContext.makeCurrent()) {
                    OkHttpCallAdviceHelper.propagateContext(call, newCallContext, errorCallback);
                    return chain.proceed(chain.request());
                } finally {
                    if (existingCallContext != null) {
                        OkHttpCallAdviceHelper.propagateContext(call, existingCallContext, errorCallback);
                    }
                }
            };

    private OkHttp3Singletons() {}
}
