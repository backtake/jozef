package com.jozef.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jozef.core.action.Action;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import com.jozef.core.tool.ToolParameters;
import com.jozef.core.util.Dates;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Weather forecast from Open-Meteo (free, no API key, no account). Only the city
 * name and its coordinates leave the computer.
 */
public class WeatherAction implements Action {

    private final String geocodingUrl;
    private final String forecastUrl;
    private final String defaultCity;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public WeatherAction(String defaultCity) {
        this(defaultCity, "https://geocoding-api.open-meteo.com/v1/search", "https://api.open-meteo.com/v1/forecast");
    }

    WeatherAction(String defaultCity, String geocodingUrl, String forecastUrl) {
        this.defaultCity = defaultCity == null ? "" : defaultCity.trim();
        this.geocodingUrl = geocodingUrl;
        this.forecastUrl = forecastUrl;
    }

    @Override
    public String name() {
        return "weather";
    }

    @Override
    public String description() {
        return "Weather forecast for a city: today, tomorrow or a date up to 7 days ahead "
                + "(temperature, rain chance, conditions). City may be omitted when the user has a default city.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return new ToolParameters()
                .string("city", "City name, e.g. Rzeszów (optional if a default city is configured)", false)
                .string("day", "today | tomorrow | yyyy-MM-dd (default today)", false)
                .build();
    }

    @Override
    public Set<String> requiredPermissions() {
        return Set.of("INTERNET");
    }

    @Override
    public ActionResult execute(ActionContext context) {
        String city = context.getString("city");
        if (city == null || city.isBlank()) city = defaultCity;
        if (city.isBlank()) {
            return ActionResult.fail("No city given and no default city configured (jozef.weather.city) — ask the user which city");
        }
        String rawDay = context.getString("day");
        String day = Dates.resolveDay(rawDay == null || rawDay.isBlank() ? "today" : rawDay);
        if (day == null) return ActionResult.fail("Day must be today, tomorrow or yyyy-MM-dd");
        try {
            JsonNode place = getJson(geocodingUrl + "?count=1&language=pl&format=json&name="
                    + URLEncoder.encode(city, StandardCharsets.UTF_8)).path("results").path(0);
            if (place.isMissingNode()) return ActionResult.fail("Unknown city: " + city);
            String url = forecastUrl + "?latitude=" + place.path("latitude").asDouble()
                    + "&longitude=" + place.path("longitude").asDouble()
                    + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code"
                    + "&current=temperature_2m,weather_code&timezone=auto&forecast_days=8";
            JsonNode forecast = getJson(url);
            JsonNode days = forecast.path("daily").path("time");
            int index = -1;
            for (int i = 0; i < days.size(); i++) {
                if (day.equals(days.get(i).asText())) index = i;
            }
            if (index < 0) return ActionResult.fail("Forecast is available only for the next 7 days");
            JsonNode daily = forecast.path("daily");
            StringBuilder sb = new StringBuilder(place.path("name").asText(city)).append(", ").append(day).append(": ")
                    .append(describe(daily.path("weather_code").path(index).asInt(-1)))
                    .append(", od ").append(Math.round(daily.path("temperature_2m_min").path(index).asDouble()))
                    .append(" do ").append(Math.round(daily.path("temperature_2m_max").path(index).asDouble()))
                    .append("°C, szansa opadów ").append(daily.path("precipitation_probability_max").path(index).asInt())
                    .append("%");
            if (index == 0 && forecast.path("current").has("temperature_2m")) {
                sb.append(". Teraz ").append(Math.round(forecast.path("current").path("temperature_2m").asDouble()))
                        .append("°C, ").append(describe(forecast.path("current").path("weather_code").asInt(-1)));
            }
            return ActionResult.ok(sb.append(".").toString());
        } catch (Exception e) {
            return ActionResult.fail("Weather service unavailable: " + e.getMessage());
        }
    }

    private JsonNode getJson(String url) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode());
        return mapper.readTree(r.body());
    }

    /** WMO weather codes in plain Polish. */
    static String describe(int code) {
        if (code == 0) return "bezchmurnie";
        if (code <= 2) return "małe zachmurzenie";
        if (code == 3) return "pochmurno";
        if (code == 45 || code == 48) return "mgła";
        if (code >= 51 && code <= 57) return "mżawka";
        if (code >= 61 && code <= 67) return "deszcz";
        if (code >= 71 && code <= 77) return "śnieg";
        if (code >= 80 && code <= 82) return "przelotne opady deszczu";
        if (code == 85 || code == 86) return "przelotne opady śniegu";
        if (code >= 95) return "burza";
        return String.format(Locale.ROOT, "warunki nieznane (kod %d)", code);
    }
}
