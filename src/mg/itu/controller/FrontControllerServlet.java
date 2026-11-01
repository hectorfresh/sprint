package mg.itu.controller;

import mg.itu.utils.ParamBinder;
import jakarta.servlet.*;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

import mg.itu.annotation.Auth;
import mg.itu.annotation.WebAPI;
import mg.itu.model.Model;
import mg.itu.model.ModelAndView;
import mg.itu.model.UrlMappingModel;
import mg.itu.model.UrlMethod;
import mg.itu.utils.Json;

import java.lang.reflect.Method;

/** Servlet frontal prenant en charge les requêtes normales et multipart/form-data. */
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,
        maxFileSize = 20L * 1024 * 1024,
        maxRequestSize = 25L * 1024 * 1024
)
public class FrontControllerServlet extends HttpServlet {

    private Map<UrlMethod, UrlMappingModel> routes;
    private String prefix = "";
    private String suffix = ".jsp";

    /** Sprint 10 : clé de session globale pour @Auth (configurée dans web.xml). */
    private String authSessionKey = null;

    @Override
    public void init() throws ServletException {
        routes = (Map<UrlMethod, UrlMappingModel>) getServletContext().getAttribute("routes");

        String initPrefix = getInitParameter("prefix");
        if (initPrefix != null) this.prefix = initPrefix;
        String initSuffix = getInitParameter("suffix");
        if (initSuffix != null) this.suffix = initSuffix;

        // Sprint 10 : nom de la clé de session à vérifier pour @Auth
        String key = getInitParameter("auth.session.key");
        if (key != null && !key.trim().isEmpty()) {
            this.authSessionKey = key.trim();
        }
    }

    private void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String contextPath = request.getContextPath();
        String url = request.getRequestURI().substring(contextPath.length());
        String reqMethod = request.getMethod();
        UrlMethod urlMethod = new UrlMethod(url, reqMethod);

        if (routes != null && routes.containsKey(urlMethod)) {
            UrlMappingModel mapping = routes.get(urlMethod);
            try {
                Method method = mapping.getMethod();

                // =====================================================
                // Sprint 10 : contrôle d'autorisation (@Auth)
                // =====================================================
                Auth auth = method.getAnnotation(Auth.class);
                if (auth == null) {
                    auth = mapping.getController().getAnnotation(Auth.class);
                }
                if (auth != null) {
                    String key = auth.key().isEmpty() ? this.authSessionKey : auth.key();
                    if (key == null || key.isEmpty()) {
                        throw new ServletException(
                                "@Auth utilisé sur "
                                + mapping.getController().getSimpleName() + "."
                                + method.getName()
                                + " mais aucun 'auth.session.key' n'est configuré dans web.xml");
                    }
                    HttpSession httpSession = request.getSession(false);
                    Object user = (httpSession == null) ? null : httpSession.getAttribute(key);
                    if (user == null) {
                        throw new ServletException(
                                "Accès refusé : la session ne contient pas la clé '"
                                + key + "' (utilisateur non connecté)");
                    }
                }

                // =====================================================
                // Instanciation du contrôleur
                // =====================================================
                Object controllerInstance = mapping.getController()
                        .getDeclaredConstructor().newInstance();

                // Détection d'un éventuel paramètre Model
                Model model = null;
                for (Class<?> parameterType : method.getParameterTypes()) {
                    if (parameterType.equals(Model.class)) {
                        model = new Model();
                        break;
                    }
                }

                // =====================================================
                // CAS 1 : @WebAPI → réponse JSON directe
                // =====================================================
                if (method.isAnnotationPresent(WebAPI.class)) {
                    WebAPI webApi = method.getAnnotation(WebAPI.class);
                    Object[] args = ParamBinder.bind(method, request, model);
                    Object returnValue = method.invoke(controllerInstance, args);

                    response.setContentType("application/json;charset=UTF-8");
                    response.setCharacterEncoding("UTF-8");
                    PrintWriter out = response.getWriter();
                    if (webApi.alreadyJson()) {
                        out.print(returnValue == null ? "null" : returnValue.toString());
                    } else if (returnValue instanceof String) {
                        out.print((String) returnValue);
                    } else {
                        out.print(Json.toJson(returnValue));
                    }
                    out.flush();
                    return;
                }

                // =====================================================
                // CAS 2 : MVC classique → forward JSP
                // =====================================================
                Object[] args = ParamBinder.bind(method, request, model);
                Object returnValue = method.invoke(controllerInstance, args);

                String viewName;
                java.util.Map<String, Object> attributes = new java.util.HashMap<>();

                if (returnValue instanceof ModelAndView) {
                    ModelAndView modelAndView = (ModelAndView) returnValue;
                    viewName = modelAndView.getViewName();
                    attributes.putAll(modelAndView.getAttributes());
                } else if (returnValue instanceof String) {
                    viewName = (String) returnValue;
                } else {
                    throw new ServletException(
                            "Return type not supported: "
                            + (returnValue == null ? "null" : returnValue.getClass().getName())
                            + ". Expected String, ModelAndView, or @WebAPI.");
                }

                if (viewName.startsWith("redirect:")) {
                    response.sendRedirect(contextPath + viewName.substring("redirect:".length()));
                    return;
                }

                if (model != null) attributes.putAll(model.getAttributes());
                for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                    request.setAttribute(entry.getKey(), entry.getValue());
                }

                String fullPath = prefix + viewName + suffix;
                request.getRequestDispatcher(fullPath).forward(request, response);

            } catch (ServletException e) {
                throw e;
            } catch (Exception e) {
                throw new ServletException("Error processing request for " + url, e);
            }
        } else {
            response.setContentType("text/html;charset=UTF-8");
            PrintWriter out = response.getWriter();
            out.println("<h2>No matching route found for: " + url + " [" + reqMethod + "]</h2>");
            out.println("<p>Available routes:</p><ul>");
            if (routes != null) {
                for (UrlMethod key : routes.keySet()) {
                    UrlMappingModel map = routes.get(key);
                    out.println("<li>" + key.getUrl() + " [" + key.getMethod() + "] -> "
                            + map.getController().getSimpleName() + "."
                            + map.getMethod().getName() + "</li>");
                }
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