package mg.itu.controller;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mg.itu.model.UrlMappingModel;
import mg.itu.utils.Utils;

public class FrontControllerServlet extends HttpServlet {

        public List<String> listControllers = new ArrayList<>();
        Map<String, UrlMappingModel> routes = new HashMap<>();

        // public List<UrlMappingModel> urlMappings = new ArrayList<>();

        @Override
        public void init() throws ServletException {
                String packageName = getServletContext().getInitParameter("package.controller");

                List<Class<?>> controllers = Utils.getControllers(packageName);
                listControllers = Utils.classesToString(controllers);

                // urlMappings = Utils.getUrlMapping(packageName);
                routes = Utils.buildRoutingTable(packageName);

        }

        private void processRequest(HttpServletRequest request, HttpServletResponse response)
                        throws ServletException, IOException {
                response.setContentType("text/html");
                PrintWriter out = response.getWriter();
                String urlMain = request.getRequestURL().toString();
                out.println(urlMain);

                String contextPath = request.getContextPath();
                String url = request.getRequestURI().substring(contextPath.length());

                for (String class1 : listControllers) {
                        out.println(class1);
                }

                if (routes.containsKey(url)) {
                        UrlMappingModel mapping = routes.get(url);
                        out.println(mapping.getController().getSimpleName());
                        out.println(mapping.getMethod().getName());
                        out.println(mapping.getUrl());
                } else {
                        for (String key : routes.keySet()) {
                                UrlMappingModel map = routes.get(key);
                                out.println(
                                                key + " -> " +
                                                                map.getController().getSimpleName() +
                                                                "." +
                                                                map.getMethod().getName());
                        }
                }
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                        throws ServletException, IOException {
                processRequest(request, response);
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                        throws ServletException, IOException {
                processRequest(request, response);
        }

}