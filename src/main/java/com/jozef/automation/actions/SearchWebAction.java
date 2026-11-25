package com.jozef.automation.actions;

import com.jozef.automation.launch.Launcher;
import com.jozef.core.action.Action;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import com.jozef.core.tool.ToolParameters;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Opens a web search (Google, YouTube…) in the user's browser. For questions
 * the model can answer itself it should just answer — this is for when the
 * user wants to see results.
 */
public class SearchWebAction implements Action {

    private final Launcher launcher;

    public SearchWebAction(Launcher launcher) {
        this.launcher = launcher;
    }

    @Override
    public String name() {
        return "search_web";
    }

    @Override
    public String description() {
        return "Show search results in the user's browser. Only when the user wants to SEE results "
                + "(\"wyszukaj w Google\", \"poszukaj na YouTube\"); answer plain questions yourself. "
                + "site: google (default), youtube, maps, allegro.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return new ToolParameters()
                .string("query", "The search query", true)
                .string("site", "google | youtube | maps | allegro (default google)", false)
                .build();
    }

    @Override
    public Set<String> requiredPermissions() {
        return Set.of("BROWSER");
    }

    @Override
    public ActionResult execute(ActionContext context) {
        String query = context.getString("query");
        if (query == null || query.isBlank()) {
            return ActionResult.fail("No search query provided");
        }
        String site = context.getString("site");
        String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = switch (site == null ? "google" : site.toLowerCase()) {
            case "youtube", "yt" -> "https://www.youtube.com/results?search_query=" + q;
            case "maps", "mapy" -> "https://www.google.com/maps/search/" + q;
            case "allegro" -> "https://allegro.pl/listing?string=" + q;
            default -> "https://www.google.com/search?q=" + q;
        };
        Launcher.LaunchResult result = launcher.openUrl(url);
        return result.success()
                ? ActionResult.ok("Showing search results for: " + query)
                : ActionResult.fail(result.message());
    }
}
