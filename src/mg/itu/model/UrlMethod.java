package mg.itu.model;

import java.util.Objects;

public class UrlMethod {
    private String url;
    private String method;

    public UrlMethod(String url, String method) {
        this.url = url;
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UrlMethod urlMethod = (UrlMethod) o;
        return Objects.equals(url, urlMethod.url) &&
               method != null && urlMethod.method != null && 
               method.equalsIgnoreCase(urlMethod.method);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, method != null ? method.toUpperCase() : null);
    }
}
