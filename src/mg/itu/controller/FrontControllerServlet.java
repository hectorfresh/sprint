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
import mg.itu.model.UrlMethod;
import mg.itu.utils.Utils;

public class FrontControllerServlet extends HttpServlet {

        private Map<UrlMethod, UrlMappingModel> routes = new HashMap<>();

        @Override
        public void init() throws ServletException {
                routes = (Map<UrlMethod, UrlMappingModel>) getServletContext().getAttribute("routes");
        }

        private void processRequest(HttpServletRequest request, HttpServletResponse response)
                        throws ServletException, IOException {
                response.setContentType("text/html");
                PrintWriter out = response.getWriter();
                String urlMain = request.getRequestURL().toString();
                String contextPath = request.getContextPath();
                String url = request.getRequestURI().substring(contextPath.length());

                out.println("<h2>FrontController servlet</h2>");
                out.println("<p><strong>Current URL:</strong> " + urlMain + "</p>");

                String reqMethod = request.getMethod();
                UrlMethod urlMethod = new UrlMethod(url, reqMethod);

                if (routes.containsKey(urlMethod)) {
                        UrlMappingModel mapping = routes.get(urlMethod);
                        out.println("<div style='border: 1px solid black; padding: 10px;'>");
                        out.println("<p><strong>Matched Controller:</strong> " + mapping.getController().getSimpleName()
                                        + "</p>");
                        out.println("<p><strong>Matched Method:</strong> " + mapping.getMethod().getName() + "</p>");
                        out.println("<p><strong>URL Mapping:</strong> " + mapping.getUrl() + " [" + reqMethod
                                        + "]</p>");

                        try {
                                Object controllerInstance = mapping.getController().getDeclaredConstructor()
                                                .newInstance();
                                Object returnValue = mapping.getMethod().invoke(controllerInstance);
                                if (returnValue instanceof String) {
                                        out.println("<p><strong>Return value:</strong> " + returnValue + "</p>");
                                }
                        } catch (Exception e) {
                                out.println("<p style='color: red;'><strong>Error executing method:</strong> "
                                                + e.getMessage() + "</p>");
                                e.printStackTrace(out);
                        }
                        out.println("</div>");
                } else {

                        out.println("<p style='color: red;'><strong>No matching route found for:</strong> " + url + " ["
                                        + reqMethod + "]</p>");
                        out.println("<p><strong>Available routes and invokes:</strong><br/>");

                        for (UrlMethod key : routes.keySet()) {

                                try {
                                        UrlMappingModel map = routes.get(key);
                                        Object controllerInstance = map.getController().getDeclaredConstructor()
                                                        .newInstance();
                                        Object returnValue = map.getMethod().invoke(controllerInstance);

                                        out.println(
                                                        "- " + key.getUrl() + " [" + key.getMethod() + "] -> " +
                                                                        map.getController().getSimpleName() +
                                                                        "." +
                                                                        map.getMethod().getName() + " -> " + returnValue
                                                                        + "<br/>");

                                } catch (Exception e) {
                                        out.println("<p style='color: red;'><strong>Error executing method:</strong> "
                                                        + e.getMessage() + "</p>");
                                        e.printStackTrace(out);
                                }

                        }

                        out.println("</p>");
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