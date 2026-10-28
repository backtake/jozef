package com.jozef.automation.actions;

import com.jozef.core.action.Action;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import com.jozef.core.tool.ToolParameters;

import java.util.Map;
import java.util.Set;

/**
 * Searches the web by opening the default browser with a query.
 */
public class SearchWebAction implements Action {

    @Override
    public String name() {
        return "search_web";
    }

    @Override
    public String description() {
        return "Search the web by opening the default browser with a query.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return new ToolParameters()
                .string("query", "The search query", true)
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
        try {
            String encoded = java.net.URLEncoder.encode(query, "UTF-8");
            String url = "https://www.google.com/search?q=" + encoded;
            Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start \"\" \"" + url + "\""});
            return ActionResult.ok("Searched for: " + query);
        } catch (Exception e) {
            return ActionResult.fail("Search failed: " + e.getMessage());
        }
    }
}
