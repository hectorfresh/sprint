package mg.itu.utils;

import jakarta.servlet.http.HttpServletRequest;

import mg.itu.annotation.RequestParam;
import mg.itu.model.Model;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * Construit les arguments d'une méthode de contrôleur depuis une requête HTTP.
 *
 * Règles :
 *  - Model est fourni par le framework ;
 *  - @RequestParam lit un paramètre HTTP simple ;
 *  - un paramètre de type JavaBean est construit puis ses propriétés sont
 *    remplies depuis les paramètres portant le même nom (binding objet) ;
 *  - les types simples sans @RequestParam gardent le comportement historique.
 *
 * Pour le binding objet, la classe doit exposer un constructeur sans argument
 * et des setters publics, comme un JavaBean classique.
 */
public final class ParamBinder {

    private ParamBinder() {
    }

    /**
     * Prépare les arguments destinés à Method.invoke(controller, args).
     */
    public static Object[] bind(Method method, HttpServletRequest request, Model model) {
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];

        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            Class<?> type = p.getType();

            // 1. Paramètre de contexte géré directement par le framework.
            if (type.equals(Model.class)) {
                args[i] = model;
                continue;
            }

            // 2. Paramètre simple explicitement lié avec @RequestParam.
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

            // 3. Un paramètre JavaBean devient automatiquement un objet lié.
            if (isBindableBeanType(type)) {
                args[i] = bindBean(type, request);
            } else {
                // Conserve le comportement précédent pour les types simples ou
                // les types non reconnus sans @RequestParam.
                args[i] = defaultFor(type);
            }
        }

        return args;
    }

    private static boolean isBindableBeanType(Class<?> type) {
        if (type.isPrimitive() || type.isEnum() || type.isArray()) return false;
        if (type.equals(String.class) || Number.class.isAssignableFrom(type)
                || type.equals(Boolean.class) || type.equals(Character.class)) return false;
        if (type.equals(BigDecimal.class) || type.equals(BigInteger.class)) return false;
        if (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) return false;
        if (type.getName().startsWith("java.")
                || type.getName().startsWith("javax.")
                || type.getName().startsWith("jakarta.")
                || type.getName().startsWith("jdk.")) return false;
        return !type.isInterface() && !Modifier.isAbstract(type.getModifiers());
    }

    /**
     * Crée un bean puis associe chaque paramètre HTTP à la propriété JavaBean
     * de même nom. Par exemple "montant=120.50" appelle setMontant(120.50).
     */
    private static Object bindBean(Class<?> beanType, HttpServletRequest request) {
        final Object bean;
        try {
            Constructor<?> constructor = beanType.getDeclaredConstructor();
            if (!constructor.canAccess(null)) {
                constructor.setAccessible(true);
            }
            bean = constructor.newInstance();
        } catch (ReflectiveOperationException | SecurityException e) {
            throw new IllegalArgumentException(
                    "Impossible de créer l'objet " + beanType.getName()
                    + " pour le binding : ajoutez un constructeur sans argument.", e);
        }

        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(beanType, Object.class);
            for (PropertyDescriptor property : beanInfo.getPropertyDescriptors()) {
                Method setter = property.getWriteMethod();
                if (setter == null) continue;

                String[] values = request.getParameterValues(property.getName());
                if (values == null) continue; // propriété non envoyée : garder la valeur initiale

                Class<?> propertyType = property.getPropertyType();
                Object converted;
                if (propertyType.isArray()) {
                    Class<?> componentType = propertyType.getComponentType();
                    Object array = Array.newInstance(componentType, values.length);
                    for (int j = 0; j < values.length; j++) {
                        Array.set(array, j, convert(values[j], componentType));
                    }
                    converted = array;
                } else {
                    String raw = values.length == 0 ? null : values[0];
                    // Un champ texte vide reste "" ; pour les autres types,
                    // un champ vide ne remplace pas la valeur par défaut du bean.
                    if (raw != null && raw.isEmpty() && !propertyType.equals(String.class)) {
                        continue;
                    }
                    if (raw == null) continue;
                    converted = convert(raw, propertyType);
                }

                try {
                    if (!setter.canAccess(bean)) {
                        setter.setAccessible(true);
                    }
                    setter.invoke(bean, converted);
                } catch (ReflectiveOperationException | SecurityException e) {
                    throw new IllegalArgumentException(
                            "Impossible de renseigner la propriété '" + property.getName()
                            + "' de " + beanType.getSimpleName(), e);
                }
            }
        } catch (java.beans.IntrospectionException e) {
            throw new IllegalArgumentException(
                    "Impossible d'analyser les propriétés de " + beanType.getName(), e);
        }

        return bean;
    }

    /** Conversion de valeurs HTTP vers les types Java usuels. */
    private static Object convert(String value, Class<?> type) {
        if (type.equals(String.class)) return value;
        if (type.equals(int.class) || type.equals(Integer.class)) return Integer.valueOf(value);
        if (type.equals(long.class) || type.equals(Long.class)) return Long.valueOf(value);
        if (type.equals(double.class) || type.equals(Double.class)) return Double.valueOf(value);
        if (type.equals(float.class) || type.equals(Float.class)) return Float.valueOf(value);
        if (type.equals(short.class) || type.equals(Short.class)) return Short.valueOf(value);
        if (type.equals(byte.class) || type.equals(Byte.class)) return Byte.valueOf(value);
        if (type.equals(boolean.class) || type.equals(Boolean.class)) {
            String v = value.trim().toLowerCase(Locale.ROOT);
            if (v.equals("true") || v.equals("1") || v.equals("on") || v.equals("yes")) return true;
            if (v.equals("false") || v.equals("0") || v.equals("off") || v.equals("no")) return false;
            throw new IllegalArgumentException("Valeur booléenne invalide : " + value);
        }
        if (type.equals(char.class) || type.equals(Character.class)) {
            if (value.length() != 1) {
                throw new IllegalArgumentException("Un caractère est attendu, valeur reçue : " + value);
            }
            return value.charAt(0);
        }
        if (type.equals(BigDecimal.class)) return new BigDecimal(value);
        if (type.equals(BigInteger.class)) return new BigInteger(value);
        if (type.equals(LocalDate.class)) return LocalDate.parse(value);
        if (type.equals(LocalDateTime.class)) return LocalDateTime.parse(value);
        if (type.equals(LocalTime.class)) return LocalTime.parse(value);
        if (type.equals(Date.class)) return Date.valueOf(LocalDate.parse(value));
        if (type.isEnum()) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object result = Enum.valueOf((Class<? extends Enum>) type, value.trim());
            return result;
        }

        throw new IllegalArgumentException(
                "Type non supporté pour le binding HTTP : " + type.getName());
    }

    /** Valeur par défaut pour les paramètres primitifs absents. */
    private static Object defaultFor(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type.equals(boolean.class)) return false;
        if (type.equals(char.class)) return '\0';
        if (type.equals(byte.class)) return (byte) 0;
        if (type.equals(short.class)) return (short) 0;
        if (type.equals(int.class)) return 0;
        if (type.equals(long.class)) return 0L;
        if (type.equals(float.class)) return 0f;
        if (type.equals(double.class)) return 0d;
        return null;
    }
}
