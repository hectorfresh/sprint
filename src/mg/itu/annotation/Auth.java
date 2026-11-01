package mg.itu.annotation;

import java.lang.annotation.*;

/**
 * Protège une méthode (ou toute une classe).
 *
 * - Sans attribut : la session doit contenir une valeur pour auth.session.key.
 * - Avec roles()  : la session doit AUSSI contenir AU MOINS UN des rôles listés,
 *                   stockés sous la clé auth.session.roles.key (web.xml).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Auth {

    /** Clé de session à vérifier. Vide = auth.session.key global. */
    String key() default "";

    /** Rôles autorisés (OR). Vide = aucun rôle requis. */
    String[] roles() default {};
}