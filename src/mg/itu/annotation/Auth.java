package mg.itu.annotation;

import java.lang.annotation.*;

/**
 * Protège une méthode (ou toute une classe) : la requête n'est autorisée
 * que si la session HTTP contient une valeur non nulle pour la clé
 * d'authentification configurée dans web.xml (auth.session.key).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Auth {
    /**
     * Clé de session à vérifier. Si vide (défaut), la clé globale
     * configurée dans web.xml (auth.session.key) est utilisée.
     */
    String key() default "";
}