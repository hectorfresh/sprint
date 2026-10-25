package mg.itu.utils;
import mg.itu.model.MySession;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import mg.itu.annotation.RequestParam;
import mg.itu.model.MapFile;
import mg.itu.model.Model;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
 * Construit les arguments d'une méthode de contrôleur à partir d'une requête HTTP.
 * Les objets JavaBean sont remplis automatiquement à partir des champs du formulaire.
 * Une propriété de type MapFile est associée au fichier multipart de même nom ; si
 * un fichier a été envoyé, il est enregistré localement avant l'appel du contrôleur.
 */
public final class ParamBinder {

    private ParamBinder() {
    }

    /** Prépare les arguments destinés à Method.invoke(controller, args). */
    public static Object[] bind(Method method, HttpServletRequest request, Model model) {
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];

        for (int i = 0; i < params.length; i++) {
            Parameter parameter = params[i];
            Class<?> type = parameter.getType();

            if (type.equals(Model.class)) {
                args[i] = model;
                continue;
            }


            if (type.equals(MySession.class)) {
               args[i] = new MySession(request.getSession());
                continue;
            }

            RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
            if (requestParam != null) {
                String raw = request.getParameter(requestParam.value());
                if (raw == null || raw.isEmpty()) {
                    if (!requestParam.defaultValue().isEmpty()) {
                        raw = requestParam.defaultValue();
                    } else if (requestParam.required()) {
                        throw new IllegalArgumentException(
                                "Paramètre obligatoire manquant : " + requestParam.value());
                    } else {
                        args[i] = defaultFor(type);
                        continue;
                    }
                }
                args[i] = convert(raw, type);
                continue;
            }

            if (isBindableBeanType(type)) {
                args[i] = bindBean(type, request);
            } else {
                args[i] = defaultFor(type);
            }
        }

        return args;
    }

    private static boolean isBindableBeanType(Class<?> type) {
        if (type.isPrimitive() || type.isEnum() || type.isArray()) return false;
        if (type.equals(MapFile.class)) return false; // MapFile est une propriété d'un bean de formulaire.
        if (type.equals(String.class) || Number.class.isAssignableFrom(type)
                || type.equals(Boolean.class) || type.equals(Character.class)) return false;
        if (type.equals(BigDecimal.class) || type.equals(BigInteger.class)) return false;
        if (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) return false;
        if (type.getName().startsWith("java.") || type.getName().startsWith("javax.")
                || type.getName().startsWith("jakarta.") || type.getName().startsWith("jdk.")) {
            return false;
        }
        return !type.isInterface() && !Modifier.isAbstract(type.getModifiers());
    }

    /** Crée le bean et renseigne ses propriétés à partir des champs HTTP de même nom. */
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

                Class<?> propertyType = property.getPropertyType();

                // Un champ de formulaire <input type="file" name="pieceJointe">
                // alimente une propriété MapFile nommée pieceJointe.
                if (propertyType.equals(MapFile.class)) {
                    MapFile uploadedFile = bindMapFile(property.getName(), request);
                    if (uploadedFile != null) {
                        try {
                            FileStorage.store(uploadedFile, request.getServletContext());
                        } catch (IOException e) {
                            throw new IllegalArgumentException(
                                    "Impossible d'enregistrer le fichier du champ '"
                                    + property.getName() + "'.", e);
                        }
                        invokeSetter(bean, setter, property.getName(), uploadedFile, beanType);
                    }
                    continue;
                }

                String[] values = request.getParameterValues(property.getName());
                if (values == null) continue;

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
                    if (raw == null) continue;
                    if (raw.isEmpty() && !propertyType.equals(String.class)) continue;
                    converted = convert(raw, propertyType);
                }

                invokeSetter(bean, setter, property.getName(), converted, beanType);
            }
        } catch (java.beans.IntrospectionException e) {
            throw new IllegalArgumentException(
                    "Impossible d'analyser les propriétés de " + beanType.getName(), e);
        }

        return bean;
    }

    /** Retourne null si la requête ne contient pas de fichier pour ce champ. */
    private static MapFile bindMapFile(String fieldName, HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null
                || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) {
            return null;
        }

        try {
            Part part = request.getPart(fieldName);
            if (part == null) return null;

            String submittedName = part.getSubmittedFileName();
            if (submittedName == null || submittedName.trim().isEmpty()) return null;

            // Retire le chemin local éventuel envoyé par un navigateur ou un client HTTP.
            String normalizedName = submittedName.replace('\\', '/');
            int lastSlash = normalizedName.lastIndexOf('/');
            if (lastSlash >= 0) normalizedName = normalizedName.substring(lastSlash + 1);
            if (normalizedName.trim().isEmpty()) return null;

            String nomFichier = normalizedName;
            String extension = "";
            int lastDot = normalizedName.lastIndexOf('.');
            if (lastDot > 0 && lastDot < normalizedName.length() - 1) {
                nomFichier = normalizedName.substring(0, lastDot);
                extension = normalizedName.substring(lastDot + 1);
            }

            return new MapFile(nomFichier, extension, readBytes(part));
        } catch (IOException | ServletException | IllegalStateException e) {
            throw new IllegalArgumentException(
                    "Impossible de lire le fichier envoyé pour le champ '" + fieldName + "'.", e);
        }
    }

    private static byte[] readBytes(Part part) throws IOException {
        try (InputStream input = part.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static void invokeSetter(Object bean, Method setter, String propertyName,
            Object value, Class<?> beanType) {
        try {
            if (!setter.canAccess(bean)) setter.setAccessible(true);
            setter.invoke(bean, value);
        } catch (ReflectiveOperationException | SecurityException e) {
            throw new IllegalArgumentException(
                    "Impossible de renseigner la propriété '" + propertyName
                    + "' de " + beanType.getSimpleName(), e);
        }
    }

    /** Conversion d'une valeur HTTP vers les types Java usuels. */
    private static Object convert(String value, Class<?> type) {
        if (type.equals(String.class)) return value;
        if (type.equals(int.class) || type.equals(Integer.class)) return Integer.valueOf(value);
        if (type.equals(long.class) || type.equals(Long.class)) return Long.valueOf(value);
        if (type.equals(double.class) || type.equals(Double.class)) return Double.valueOf(value);
        if (type.equals(float.class) || type.equals(Float.class)) return Float.valueOf(value);
        if (type.equals(short.class) || type.equals(Short.class)) return Short.valueOf(value);
        if (type.equals(byte.class) || type.equals(Byte.class)) return Byte.valueOf(value);
        if (type.equals(boolean.class) || type.equals(Boolean.class)) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("true") || normalized.equals("1")
                    || normalized.equals("on") || normalized.equals("yes")) return true;
            if (normalized.equals("false") || normalized.equals("0")
                    || normalized.equals("off") || normalized.equals("no")) return false;
            throw new IllegalArgumentException("Valeur booléenne invalide : " + value);
        }
        if (type.equals(char.class) || type.equals(Character.class)) {
            if (value.length() != 1) {
                throw new IllegalArgumentException(
                        "Un caractère est attendu, valeur reçue : " + value);
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
