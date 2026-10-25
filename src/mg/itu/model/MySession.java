package mg.itu.model;

import jakarta.servlet.http.HttpSession;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Wrapper de HttpSession offrant une API Map<String, Object>.
 * Une instance est créée à chaque requête, mais les données
 * sous-jacentes persistent via HttpSession (donc entre requêtes).
 */
public class MySession {

    private final HttpSession httpSession;

    public MySession(HttpSession httpSession) {
        if (httpSession == null) {
            throw new IllegalArgumentException("HttpSession ne peut pas être null");
        }
        this.httpSession = httpSession;
    }

    /* ------------------------------------------------------------------ */
    /* API type Map<String, Object>                                        */
    /* ------------------------------------------------------------------ */

    /** Stocke une valeur en session. */
    public void set(String key, Object value) {
        httpSession.setAttribute(key, value);
    }

    /** Alias de set() — pratique pour "ajouter". */
    public void add(String key, Object value) {
        set(key, value);
    }

    /** Récupère une valeur brute. */
    public Object get(String key) {
        return httpSession.getAttribute(key);
    }

    /** Récupère une valeur typée. */
    public <T> T get(String key, Class<T> type) {
        Object value = httpSession.getAttribute(key);
        return value == null ? null : type.cast(value);
    }

    /** Récupère une String, ou null. */
    public String getString(String key) {
        Object value = httpSession.getAttribute(key);
        return value == null ? null : value.toString();
    }

    /** Récupère un Integer, ou null. */
    public Integer getInt(String key) {
        Object value = httpSession.getAttribute(key);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    /** Récupère un Boolean, ou null. */
    public Boolean getBoolean(String key) {
        Object value = httpSession.getAttribute(key);
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    /** Récupère ou crée une valeur à la volée. */
    public <T> T getOrCreate(String key, Class<T> type, Supplier<T> supplier) {
        Object existing = httpSession.getAttribute(key);
        if (existing != null) return type.cast(existing);
        T created = supplier.get();
        httpSession.setAttribute(key, created);
        return created;
    }

    /** Teste la présence d'une clé. */
    public boolean contains(String key) {
        return httpSession.getAttribute(key) != null;
    }

    /** Supprime une clé. */
    public void remove(String key) {
        httpSession.removeAttribute(key);
    }

    /** Supprime toutes les clés (sans invalider la session). */
    public void clear() {
        Enumeration<String> names = httpSession.getAttributeNames();
        while (names.hasMoreElements()) {
            httpSession.removeAttribute(names.nextElement());
        }
    }

    /** Copie de toutes les valeurs (lecture seule). */
    public Map<String, Object> getAttributes() {
        Map<String, Object> copy = new HashMap<>();
        Enumeration<String> names = httpSession.getAttributeNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            copy.put(name, httpSession.getAttribute(name));
        }
        return Collections.unmodifiableMap(copy);
    }

    /* ------------------------------------------------------------------ */
    /* Cycle de vie de la session                                          */
    /* ------------------------------------------------------------------ */

    /** Invalide la session (déconnexion). */
    public void invalidate() {
        httpSession.invalidate();
    }

    /* ------------------------------------------------------------------ */
    /* Métadonnées                                                         */
    /* ------------------------------------------------------------------ */

    public String getId() {
        return httpSession.getId();
    }

    public boolean isNew() {
        return httpSession.isNew();
    }

    public long getCreationTime() {
        return httpSession.getCreationTime();
    }

    public long getLastAccessedTime() {
        return httpSession.getLastAccessedTime();
    }

    public int getMaxInactiveInterval() {
        return httpSession.getMaxInactiveInterval();
    }

    public void setMaxInactiveInterval(int seconds) {
        httpSession.setMaxInactiveInterval(seconds);
    }

    /** Accès à la HttpSession brute si besoin. */
    public HttpSession getHttpSession() {
        return httpSession;
    }
}