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
import mg.itu.exception.AuthException;
import mg.itu.model.Model;
import mg.itu.model.ModelAndView;
import mg.itu.model.UrlMappingModel;
import mg.itu.model.UrlMethod;
import mg.itu.utils.Json;

import java.lang.reflect.Method;

/** Servlet frontal : MVC, REST JSON, session, autorisation, contrôleurs singleton. */
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,
        maxFileSize = 20L * 1024 * 1024,
        maxRequestSize = 25L * 1024 * 1024
)
public class FrontControllerServlet extends HttpServlet {

    private Map<UrlMethod, UrlMappingModel> routes;
    private Map<Class<?>, Object> controllers;   // Sprint 11
    private String prefix = "";
    private String suffix = ".jsp";
    private String authSessionKey = null;
    private String authSessionRolesKey = null;

    @Override
    public void init() throws ServletException {
        routes = (Map<UrlMethod, UrlMappingModel>) getServletContext().getAttribute("routes");
        controllers = (Map<Class<?>, Object>) getServletContext().getAttribute("controllers");

        if (routes == null) {
            throw new ServletException(
                    "Table de routage absente : vérifiez que FrameworkContextListener est déclaré dans web.xml");
        }
        if (controllers == null) {
            throw new ServletException(
                    "Map des contrôleurs absente : le listener n'a pas initialisé les singletons");
        }

        String initPrefix = getInitParameter("prefix");
        if (initPrefix != null) this.prefix = initPrefix;
        String initSuffix = getInitParameter("suffix");
        if (initSuffix != null) this.suffix = initSuffix;

        String key = getInitParameter("auth.session.key");
        if (key != null && !key.trim().isEmpty()) {
            this.authSessionKey = key.trim();
        }
        String rolesKey = getInitParameter("auth.session.roles.key");
        if (rolesKey != null && !rolesKey.trim().isEmpty()) {
            this.authSessionRolesKey = rolesKey.trim();
        }
    }

    private void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String contextPath = request.getContextPath();
        String url = request.getRequestURI().substring(contextPath.length());
        String reqMethod = request.getMethod();
        UrlMethod urlMethod = new UrlMethod(url, reqMethod);

        if (routes.containsKey(urlMethod)) {
            UrlMappingModel mapping = routes.get(urlMethod);
            try {
                Method method = mapping.getMethod();

                // =====================================================
                // Contrôle d'autorisation (@Auth)
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
                        throw AuthException.unauthorized(
                                "Non authentifié : la session ne contient pas la clé '"
                                + key + "'");
                    }

                    String[] requiredRoles = auth.roles();
                    if (requiredRoles.length > 0) {
                        if (this.authSessionRolesKey == null || this.authSessionRolesKey.isEmpty()) {
                            throw new ServletException(
                                    "@Auth(roles=...) utilisé sur "
                                    + mapping.getController().getSimpleName() + "."
                                    + method.getName()
                                    + " mais 'auth.session.roles.key' n'est pas configuré dans web.xml");
                        }
                        Object rolesValue = httpSession.getAttribute(this.authSessionRolesKey);
                        java.util.Set<String> userRoles = extractRoles(rolesValue);

                        boolean allowed = false;
                        for (String r : requiredRoles) {
                            if (r != null && userRoles.contains(r.trim())) {
                                allowed = true;
                                break;
                            }
                        }
                        if (!allowed) {
                            throw AuthException.forbidden(
                                    "Rôles insuffisants. Requis : "
                                    + java.util.Arrays.toString(requiredRoles)
                                    + ", rôles actuels : " + userRoles);
                        }
                    }
                }

                // =====================================================
                // Sprint 11 : récupérer le singleton (au lieu de newInstance)
                // =====================================================
                Object controllerInstance = controllers.get(mapping.getController());
                if (controllerInstance == null) {
                    throw new ServletException(
                            "Contrôleur non instancié : " + mapping.getController().getName());
                }

                Model model = null;
                for (Class<?> pt : method.getParameterTypes()) {
                    if (pt.equals(Model.class)) {
                        model = new Model();
                        break;
                    }
                }

                // =====================================================
                // CAS 1 : @WebAPI → JSON
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
                // CAS 2 : MVC classique
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
                    String target = viewName.substring("redirect:".length());
                    response.sendRedirect(contextPath + target);
                    return;
                }

                if (model != null) attributes.putAll(model.getAttributes());
                for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                    request.setAttribute(entry.getKey(), entry.getValue());
                }

                String fullPath = prefix + viewName + suffix;
                request.getRequestDispatcher(fullPath).forward(request, response);

            } catch (AuthException e) {
                response.sendError(e.getStatusCode(), e.getMessage());
                return;
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
            for (UrlMethod key : routes.keySet()) {
                UrlMappingModel map = routes.get(key);
                out.println("<li>" + key.getUrl() + " [" + key.getMethod() + "] -> "
                        + map.getController().getSimpleName() + "."
                        + map.getMethod().getName() + "</li>");
            }
            out.println("</ul>");
        }
    }

    private java.util.Set<String> extractRoles(Object value) {
        java.util.Set<String> roles = new java.util.HashSet<>();
        if (value == null) return roles;

        if (value instanceof java.util.Collection) {
            for (Object o : (java.util.Collection<?>) value) {
                if (o != null) roles.add(o.toString().trim());
            }
        } else if (value.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < len; i++) {
                Object o = java.lang.reflect.Array.get(value, i);
                if (o != null) roles.add(o.toString().trim());
            }
        } else {
            for (String s : value.toString().split(",")) {
                if (!s.trim().isEmpty()) roles.add(s.trim());
            }
        }
        return roles;
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