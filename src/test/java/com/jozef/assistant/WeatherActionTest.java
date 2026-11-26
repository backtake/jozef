package com.jozef.assistant;

import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherActionTest {

    private HttpServer server;
    private String base;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String today = LocalDate.now().toString();
        String tomorrow = LocalDate.now().plusDays(1).toString();
        respond("/geo", "{\"results\":[{\"name\":\"Rzeszów\",\"latitude\":50.04,\"longitude\":22.0}]}");
        respond("/forecast", """
                {"current":{"temperature_2m":3.4,"weather_code":3},
                 "daily":{"time":["%s","%s"],"temperature_2m_max":[5.2,8.9],"temperature_2m_min":[-1.4,2.1],
                          "precipitation_probability_max":[10,80],"weather_code":[3,61]}}
                """.formatted(today, tomorrow));
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void respond(String path, String body) {
        server.createContext(path, ex -> {
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void tomorrowInTheDefaultCity() {
        WeatherAction weather = new WeatherAction("Rzeszów", base + "/geo", base + "/forecast");
        ActionResult r = weather.execute(ActionContext.of(Map.of("day", "tomorrow")));

        assertThat(r.success()).isTrue();
        assertThat(r.message()).contains("Rzeszów").contains("deszcz").contains("od 2 do 9°C").contains("80%");
    }

    @Test
    void todayIncludesCurrentConditions() {
        WeatherAction weather = new WeatherAction("", base + "/geo", base + "/forecast");
        ActionResult r = weather.execute(ActionContext.of(Map.of("city", "Rzeszów")));
        assertThat(r.message()).contains("Teraz 3°C").contains("pochmurno");
    }

    @Test
    void asksForACityWhenNoneIsKnown() {
        ActionResult r = new WeatherAction("", base + "/geo", base + "/forecast").execute(ActionContext.of(Map.of()));
        assertThat(r.success()).isFalse();
        assertThat(r.message()).contains("ask the user");
    }
}
