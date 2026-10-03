package mg.itu.controller;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

import mg.itu.model.Model;
import mg.itu.model.ModelAndView;
import mg.itu.model.UrlMappingModel;
import mg.itu.model.UrlMethod;
import java.lang.reflect.Method;
import mg.itu.annotation.WebAPI;
import mg.itu.utils.Json;

public class FrontControllerServlet extends HttpServlet {

    private Map<UrlMethod, UrlMappingModel> routes;
    private String prefix = "";
    private String suffix = ".jsp";

    @Override
    public void init() throws ServletException {
        // Récupération de la table de routage depuis le contexte
        routes = (Map<UrlMethod, UrlMappingModel>) getServletContext().getAttribute("routes");

        // Paramètres de configuration de la vue
        String initPrefix = getInitParameter("prefix");
        if (initPrefix != null) {
            this.prefix = initPrefix;
        }
        String initSuffix = getInitParameter("suffix");
        if (initSuffix != null) {
            this.suffix = initSuffix;
        }
    }

private void processRequest(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

    String contextPath = request.getContextPath();
    String url = request.getRequestURI().substring(contextPath.length());
    String reqMethod = request.getMethod();

    UrlMethod urlMethod = new UrlMethod(url, reqMethod);

    // Si une route correspond
    if (routes.containsKey(urlMethod)) {
        UrlMappingModel mapping = routes.get(urlMethod);

        try {
            Object controllerInstance = mapping.getController()
                    .getDeclaredConstructor().newInstance();

            Method method = mapping.getMethod();

            // =====================================================
            // CAS 1 : méthode annotée @WebAPI → réponse JSON directe
            // =====================================================
            if (method.isAnnotationPresent(WebAPI.class)) {

                WebAPI webApi = method.getAnnotation(WebAPI.class);

                Object returnValue = method.invoke(controllerInstance);

                response.setContentType("application/json;charset=UTF-8");
                response.setCharacterEncoding("UTF-8");
                PrintWriter out = response.getWriter();

                if (webApi.alreadyJson()) {
                    // Déjà du JSON → écrit tel quel
                    out.print(returnValue == null ? "null" : returnValue.toString());
                } else if (returnValue instanceof String) {
                    // String → pas de transformation (méthode 2)
                    out.print((String) returnValue);
                } else {
                    // Object / List / Map / ... → JSON (méthode 1)
                    out.print(Json.toJson(returnValue));
                }
                out.flush();
                return;   // ⚠️ pas de forward, on s'arrête ici
            }

            // =====================================================
            // CAS 2 : comportement MVC classique (dispatch vers JSP)
            // =====================================================
            Model model = null;
            Object[] args = new Object[0];
            if (method.getParameterCount() == 1
                && method.getParameterTypes()[0].equals(Model.class)) {
                model = new Model();
                args = new Object[] { model };
            }

            Object returnValue = method.invoke(controllerInstance, args);

            String viewName = null;
            java.util.Map<String, Object> attributes = new java.util.HashMap<>();

            if (returnValue instanceof ModelAndView) {
                ModelAndView mav = (ModelAndView) returnValue;
                viewName = mav.getViewName();
                attributes.putAll(mav.getAttributes());
            } else if (returnValue instanceof String) {
                viewName = (String) returnValue;
            } else {
                throw new ServletException(
                    "Return type not supported: "
                    + (returnValue == null ? "null" : returnValue.getClass().getName())
                    + ". Expected String, ModelAndView, or @WebAPI.");
            }

            if (model != null) attributes.putAll(model.getAttributes());

            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                request.setAttribute(entry.getKey(), entry.getValue());
            }

            String fullPath = prefix + viewName + suffix;
            request.getRequestDispatcher(fullPath).forward(request, response);

        } catch (Exception e) {
            throw new ServletException("Error processing request for " + url, e);
        }

    } else {
        // Aucune route trouvée → page de débogage (inchangée)
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        out.println("<h2>No matching route found for: " + url + " [" + reqMethod + "]</h2>");
        out.println("<p>Available routes:</p><ul>");
        for (UrlMethod key : routes.keySet()) {
            UrlMappingModel map = routes.get(key);
            out.println("<li>" + key.getUrl() + " [" + key.getMethod() + "] -> "
                + map.getController().getSimpleName() + "." + map.getMethod().getName() + "</li>");
        }
        out.println("</ul>");
    }
}
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        processRequest(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        processRequest(req, resp);
    }
}