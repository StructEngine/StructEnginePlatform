package com.structengine.platform.game.service;

import com.structengine.platform.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseUrlResolverTest {

    @Test
    void derivesFromHostHeader() {
        AppProperties props = new AppProperties();
        BaseUrlResolver resolver = new BaseUrlResolver(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.addHeader("Host", "build.example.com:8443");

        assertEquals("https://build.example.com:8443", resolver.resolve(request));
    }

    @Test
    void prefersConfiguredPublicBaseUrl() {
        AppProperties props = new AppProperties();
        props.setPublicBaseUrl("https://cdn.example.com///");
        BaseUrlResolver resolver = new BaseUrlResolver(props);

        assertEquals("https://cdn.example.com", resolver.resolve(new MockHttpServletRequest()));
    }

    @Test
    void honorsForwardedHeaders() {
        AppProperties props = new AppProperties();
        BaseUrlResolver resolver = new BaseUrlResolver(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "map.example.com");

        assertEquals("https://map.example.com", resolver.resolve(request));
    }
}
