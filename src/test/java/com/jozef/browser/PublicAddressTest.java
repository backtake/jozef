package com.jozef.browser;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PublicAddressTest {

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8080/admin", "http://127.0.0.1/", "http://192.168.1.1/", "http://10.0.0.5/x",
            "http://172.16.3.4/", "http://169.254.169.254/latest/meta-data", "http://[::1]/", "http://router.lan/",
            "http://nas.local/", "http://100.64.1.1/", "http://0.0.0.0/", "http:///nohost"})
    void privateAndLocalAddressesAreRefused(String url) {
        assertThat(BrowserCapability.isPublicAddress(url)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://8.8.8.8/", "http://1.1.1.1/"})
    void publicAddressesAreAllowed(String url) {
        assertThat(BrowserCapability.isPublicAddress(url)).isTrue();
    }
}
