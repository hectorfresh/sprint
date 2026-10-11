package mg.itu.utils;

import jakarta.servlet.http.HttpServletRequest;

import mg.itu.annotation.RequestParam;
import mg.itu.model.Model;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class ParamBinder {

    /**
     * Construit le tableau d'arguments pour method.invoke(controller, args).
     *
     * @param method  la méthode du contrôleur
     * @param request la requête HTTP
     * @param model   référence à un Model déjà créé (peut être null)
     * @return un tableau d'objets, un par paramètre de la méthode
     */
    public static Object[] bind(Method method, HttpServletRequest request, Model model) {

        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];

        for (int i = 0; i < params.length; i++) {

            Parameter p = params[i];
            Class<?> type = p.getType();

            // Cas 1 : paramètre de type Model → on utilise l'instance fournie
            if (type.equals(Model.class)) {
                args[i] = model;
                continue;
            }

            // Cas 2 : paramètre annoté @RequestParam → lecture depuis la requête
            RequestParam rp = p.getAnnotation(RequestParam.class);
            if (rp != null) {
                String raw = request.getParameter(rp.value());

                if (raw == null || raw.isEmpty()) {
                    if (!rp.defaultValue().isEmpty()) {
                        raw = rp.defaultValue();
                    } else if (rp.required()) {
                        throw new IllegalArgumentException(
                            "Paramètre obligatoire manquant : " + rp.value());
                    } else {
                        args[i] = defaultFor(type);
                        continue;
                    }
                }
                args[i] = convert(raw, type);
                continue;
            }

            // Cas 3 : pas d'annotation → on laisse null (ou valeur par défaut)
            args[i] = defaultFor(type);
        }

        return args;
    }

    /* ------------------------------------------------------------------ */
    /* Conversion d'une String vers le type Java demandé                   */
    /* ------------------------------------------------------------------ */
    private static Object convert(String value, Class<?> type) {
        if (type.equals(String.class))   return value;
        if (type.equals(int.class)     || type.equals(Integer.class))  return Integer.parseInt(value);
        if (type.equals(long.class)    || type.equals(Long.class))     return Long.parseLong(value);
        if (type.equals(double.class)  || type.equals(Double.class))   return Double.parseDouble(value);
        if (type.equals(float.class)   || type.equals(Float.class))    return Float.parseFloat(value);
        if (type.equals(boolean.class) || type.equals(Boolean.class))  return Boolean.parseBoolean(value);
        if (type.equals(short.class)   || type.equals(Short.class))    return Short.parseShort(value);
        if (type.equals(byte.class)    || type.equals(Byte.class))     return Byte.parseByte(value);

        throw new IllegalArgumentException(
            "Type non supporté pour @RequestParam : " + type.getName());
    }

    /* ------------------------------------------------------------------ */
    /* Valeur par défaut pour un type primitif (si paramètre absent)       */
    /* ------------------------------------------------------------------ */
    private static Object defaultFor(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type.equals(boolean.class)) return false;
        if (type.equals(char.class))    return '\0';
        if (type.equals(byte.class))    return (byte) 0;
        if (type.equals(short.class))   return (short) 0;
        if (type.equals(int.class))     return 0;
        if (type.equals(long.class))    return 0L;
        if (type.equals(float.class))   return 0f;
        if (type.equals(double.class))  return 0d;
        return null;
    }
}