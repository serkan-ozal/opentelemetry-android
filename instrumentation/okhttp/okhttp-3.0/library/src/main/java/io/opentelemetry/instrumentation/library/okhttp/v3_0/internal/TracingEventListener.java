package io.opentelemetry.instrumentation.library.okhttp.v3_0.internal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.HttpUrl;

public class TracingEventListener extends EventListener {

    private final Tracer tracer;
    private final long callId;
    private final long callStartNanos;

    private volatile Span callSpan;
    private volatile Context callContext;
    private final Map<OkHttpEvent, Span> eventSpans = new ConcurrentHashMap<>();

    public TracingEventListener(Tracer tracer,
                                long callId, long callStartNanos) {
        this.tracer = tracer;
        this.callId = callId;
        this.callStartNanos = callStartNanos;
    }

    private void printEvent(String name) {
        long elapsedNanos = System.nanoTime() - callStartNanos;
        System.out.printf("%04d %.3f %s%n", callId, elapsedNanos / 1000000000d, name);
    }

    private Span startCallSpan(Call call) {
        HttpUrl requestUrl = call.request().url();
        return tracer.spanBuilder("HTTP Call" + " - " + requestUrl)
                .startSpan();
    }

    private Span startEventSpan(Call call, OkHttpEvent event) {
        Span span = tracer.spanBuilder(event.getName())
                .setParent(callContext)
                .startSpan();
        eventSpans.put(event, span);
        return span;
    }

    private Span endEventSpan(Call call, OkHttpEvent event) {
        Span span = eventSpans.remove(event);
        if (span != null) {
            span.end();
        }
        return span;
    }

    private boolean shouldIgnore(Call call) {
        if (call.request().url().host().contains("otel-collector")) {
            return true;
        }
        return false;
    }

    @SuppressWarnings("MustBeClosedChecker")
    @Override
    public void callStart(Call call) {
        if (shouldIgnore(call)) {
            return;
        }

        callSpan = startCallSpan(call);
        callContext = Context.current().with(callSpan);

        OkHttpCallAdviceHelper.propagateContext(call, callContext, (error) -> {
            System.out.println(">>> On error: " + error.getMessage());
        });

        printEvent("callStart");
    }

    @Override
    public void callEnd(Call call) {
        if (shouldIgnore(call)) {
            return;
        }

        if (callSpan != null) {
            callSpan.end();
        }

        printEvent("callEnd");
    }

    @Override
    public void callFailed(Call call, IOException ioe) {
        if (shouldIgnore(call)) {
            return;
        }

        if (callSpan != null) {
            callSpan.end();
        }

        printEvent("callFailed");
    }

    @Override
    public void dnsStart(Call call, String domainName) {
        if (shouldIgnore(call)) {
            return;
        }

        startEventSpan(call, OkHttpEvent.DNS_RESOLVE);

        printEvent("dnsStart");
    }

    @Override
    public void dnsEnd(Call call, String domainName, List<InetAddress> inetAddressList) {
        if (shouldIgnore(call)) {
            return;
        }

        endEventSpan(call, OkHttpEvent.DNS_RESOLVE);

        printEvent("dnsEnd");
    }

    @Override
    public void secureConnectStart(@NotNull Call call) {
        if (shouldIgnore(call)) {
            return;
        }

        startEventSpan(call, OkHttpEvent.SSL_HANDSHAKE);

        printEvent("sslStart");
    }

    @Override
    public void secureConnectEnd(@NotNull Call call, @Nullable Handshake handshake) {
        if (shouldIgnore(call)) {
            return;
        }

        endEventSpan(call, OkHttpEvent.SSL_HANDSHAKE);

        printEvent("sslEnd");
    }

}
