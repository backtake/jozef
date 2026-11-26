package com.jozef.browser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class HostCacheTest {

    private final AtomicBoolean online = new AtomicBoolean(false);
    private final AtomicBoolean resolvesPrivate = new AtomicBoolean(false);
    private final AtomicInteger lookups = new AtomicInteger();
    private final AtomicLong now = new AtomicLong(1_000_000);
    private BrowserCapability.Resolver original;
    private java.util.function.LongSupplier originalClock;

    @BeforeEach
    void setUp() {
        original = BrowserCapability.resolver;
        originalClock = BrowserCapability.clock;
        BrowserCapability.clearHostCache();
        BrowserCapability.clock = now::get;
        BrowserCapability.resolver = host -> {
            lookups.incrementAndGet();
            if (!online.get()) throw new UnknownHostException(host);
            return new InetAddress[]{InetAddress.getByAddress(host,
                    resolvesPrivate.get() ? new byte[]{(byte) 192, (byte) 168, 1, 1} : new byte[]{13, 107, 21, (byte) 200})};
        };
    }

    @AfterEach
    void tearDown() {
        BrowserCapability.resolver = original;
        BrowserCapability.clock = originalClock;
        BrowserCapability.clearHostCache();
    }

    @Test
    void failedLookupIsNotCachedSoTheHostWorksOnceTheNetworkIsBack() {
        assertThat(BrowserCapability.isPublicAddressCached("https://www.bing.com/search")).isFalse(); // offline after wake-up
        online.set(true);
        assertThat(BrowserCapability.isPublicAddressCached("https://www.bing.com/search")).isTrue();
    }

    @Test
    void definiteAnswersAreCachedWithOneLookupAndExpire() {
        online.set(true);
        assertThat(BrowserCapability.isPublicAddressCached("https://a.example/x")).isTrue();
        assertThat(BrowserCapability.isPublicAddressCached("https://a.example/y.png")).isTrue();
        assertThat(lookups).hasValue(1);

        resolvesPrivate.set(true); // DNS rebinding
        now.addAndGet(61_000);
        assertThat(BrowserCapability.isPublicAddressCached("https://a.example/z")).isFalse();
        assertThat(lookups).hasValue(2);
    }
}
