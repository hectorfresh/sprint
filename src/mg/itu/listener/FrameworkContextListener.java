package mg.itu.listener;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

import mg.itu.model.UrlMappingModel;
import mg.itu.model.UrlMethod;
import mg.itu.utils.Utils;

public class FrameworkContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {

        ServletContext context = sce.getServletContext();

        String packageName = context.getInitParameter("package.controller");

        // 1. Construire la table de routage
        Map<UrlMethod, UrlMappingModel> routes = new HashMap<>();
        Utils.buildRoutingTable(packageName, routes);

        // 2. Sprint 11 : instancier chaque contrôleur UNE SEULE FOIS
        Map<Class<?>, Object> controllers = new HashMap<>();
        for (UrlMappingModel mapping : routes.values()) {
            Class<?> controllerClass = mapping.getController();
            if (!controllers.containsKey(controllerClass)) {
                try {
                    Object instance = controllerClass
                            .getDeclaredConstructor()
                            .newInstance();
                    controllers.put(controllerClass, instance);
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Impossible d'instancier le contrôleur "
                            + controllerClass.getName()
                            + " (constructeur sans argument requis)", e);
                }
            }
        }

        // 3. Stocker les deux maps dans le ServletContext
        context.setAttribute("routes", routes);
        context.setAttribute("controllers", controllers);

        context.log("[Framework] " + routes.size() + " route(s), "
                + controllers.size() + " contrôleur(s) instancié(s) en singleton");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}