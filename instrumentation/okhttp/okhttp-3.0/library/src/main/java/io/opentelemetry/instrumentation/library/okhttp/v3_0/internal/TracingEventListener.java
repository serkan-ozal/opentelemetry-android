package io.opentelemetry.instrumentation.library.okhttp.v3_0.internal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.Handshake;

public class TracingEventListener extends EventListener {

    private final Instrumenter<OkHttpEvent, Void> instrumenter;
    private final long callId;
    private final long callStartNanos;

    private Instant dnsResolveStartTime;
    private Instant sslHandshakeStartTime;

    public TracingEventListener(Instrumenter<OkHttpEvent, Void> instrumenter,
                                long callId, long callStartNanos) {
        this.instrumenter = instrumenter;
        this.callId = callId;
        this.callStartNanos = callStartNanos;
    }

    private void printEvent(String name) {
        long elapsedNanos = System.nanoTime() - callStartNanos;
        System.out.printf("%04d %.3f %s%n", callId, elapsedNanos / 1000000000d, name);
    }

    private void createEventSpan(OkHttpEvent event, Instant eventStartTime) {
        if (eventStartTime != null) {
            InstrumenterUtil.startAndEnd(
                    instrumenter,
                    Context.current(),
                    event,
                    null,
                    null,
                    eventStartTime,
                    Instant.now());
        }
    }

    private boolean shouldIgnore(Call call) {
        if (call.request().url().host().contains("otel-collector")) {
            return true;
        }
        return false;
    }

    @Override
    public void callStart(Call call) {
        if (shouldIgnore(call)) {
            return;
        }
        printEvent("callStart");
    }

    @Override
    public void callEnd(Call call) {
        if (shouldIgnore(call)) {
            return;
        }
        printEvent("callEnd");
    }

    @Override
    public void dnsStart(Call call, String domainName) {
        if (shouldIgnore(call)) {
            return;
        }

        dnsResolveStartTime = Instant.now();

        printEvent("dnsStart");
    }

    @Override
    public void dnsEnd(Call call, String domainName, List<InetAddress> inetAddressList) {
        if (shouldIgnore(call)) {
            return;
        }

        createEventSpan(OkHttpEvent.DNS_RESOLVE, dnsResolveStartTime);

        printEvent("dnsEnd");
    }

    @Override
    public void secureConnectStart(@NotNull Call call) {
        if (shouldIgnore(call)) {
            return;
        }

        sslHandshakeStartTime = Instant.now();

        printEvent("sslStart");
    }

    @Override
    public void secureConnectEnd(@NotNull Call call, @Nullable Handshake handshake) {
        if (shouldIgnore(call)) {
            return;
        }

        createEventSpan(OkHttpEvent.SSL_HANDSHAKE, sslHandshakeStartTime);

        printEvent("sslEnd");
    }

}
