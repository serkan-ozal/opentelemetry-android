/*
 * Copyright Splunk Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.splunk.rum;

import android.app.Application;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SplunkRumTest {

    @Before
    public void setUp() {
        SplunkRum.resetSingletonForTest();
    }

    @Test
    public void initialization_onlyOnce() {
        Config config = mock(Config.class);
        when(config.getBeaconUrl()).thenReturn("http://backend");
        when(config.isDebugEnabled()).thenReturn(true);
        SplunkRum singleton = SplunkRum.initialize(config, mock(Application.class));
        SplunkRum sameInstance = SplunkRum.initialize(config, mock(Application.class));

        assertSame(singleton, sameInstance);
    }

    @Test
    public void getInstance_preConfig() {
        assertThrows(IllegalStateException.class, SplunkRum::getInstance);
    }

    @Test
    public void getInstance() {
        Config config = mock(Config.class);
        when(config.getBeaconUrl()).thenReturn("http://backend");
        SplunkRum singleton = SplunkRum.initialize(config, mock(Application.class));
        assertSame(singleton, SplunkRum.getInstance());
    }

    @Test
    public void newConfigBuilder() {
        assertNotNull(SplunkRum.newConfigBuilder());
    }

    @Test
    public void addEvent() {
        InMemorySpanExporter testExporter = InMemorySpanExporter.create();
        OpenTelemetrySdk testSdk = buildTestSdk(testExporter);

        SplunkRum splunkRum = new SplunkRum(mock(Config.class), testSdk, new SessionId());

        Attributes attributes = Attributes.of(stringKey("one"), "1", longKey("two"), 2L);
        splunkRum.addRumEvent("foo", attributes);

        List<SpanData> spans = testExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());
        assertEquals("foo", spans.get(0).getName());
        assertEquals(attributes.asMap(), spans.get(0).getAttributes().asMap());
    }

    @Test
    public void addException() {
        InMemorySpanExporter testExporter = InMemorySpanExporter.create();
        OpenTelemetrySdk testSdk = buildTestSdk(testExporter);

        SplunkRum splunkRum = new SplunkRum(mock(Config.class), testSdk, new SessionId());

        Attributes attributes = Attributes.of(stringKey("one"), "1", longKey("two"), 2L);
        splunkRum.addRumException("fooError", attributes, new NullPointerException("oopsie"));

        List<SpanData> spans = testExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());
        assertEquals("fooError", spans.get(0).getName());

        Attributes expected = attributes.toBuilder()
                .put(SemanticAttributes.EXCEPTION_MESSAGE, "oopsie")
                .put(SplunkRum.ERROR_MESSAGE_KEY, "oopsie")
                .put(SemanticAttributes.EXCEPTION_TYPE, "NullPointerException")
                .put(SplunkRum.ERROR_TYPE_KEY, "NullPointerException")
                .build();

        assertEquals(expected.asMap(), spans.get(0).getAttributes().asMap());
    }

    private OpenTelemetrySdk buildTestSdk(InMemorySpanExporter testExporter) {
        OpenTelemetrySdk testSdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(testExporter))
                        .build())
                .build();
        return testSdk;
    }
}