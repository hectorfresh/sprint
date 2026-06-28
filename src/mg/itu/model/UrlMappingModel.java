package mg.itu.model;

import java.lang.reflect.Method;

public class UrlMappingModel {

    private Class<?> controller;
    private Method method;
    private String url;

    public UrlMappingModel(){}
    
    public UrlMappingModel(Class<?> controller, Method method, String url) {
        this.controller = controller;
        this.method = method;
        this.url = url;
    }
    public Class<?> getController() {
        return controller;
    }
    public void setController(Class<?> controller) {
        this.controller = controller;
    }
    public Method getMethod() {
        return method;
    }
    public void setMethod(Method method) {
        this.method = method;
    }
    public String getUrl() {
        return url;
    }
    public void setUrl(String url) {
        this.url = url;
    }


    
}
