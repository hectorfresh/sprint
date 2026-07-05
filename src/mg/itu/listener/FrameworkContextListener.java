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

        Map<UrlMethod, UrlMappingModel> routes = new HashMap<>();

        Utils.buildRoutingTable(packageName, routes);

        context.setAttribute("routes", routes);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {

    }
}