package mg.itu.model;

import java.util.HashMap;
import java.util.Map;

public class ModelAndView {
    private String viewName;
    private final Map<String, Object> attributes = new HashMap<>();

    public ModelAndView(String viewName) {
        this.viewName = viewName;
    }

    public void addAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public String getViewName() {
        return viewName;
    }

    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }
}