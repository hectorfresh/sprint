package mg.itu.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface WebAPI {
    /**
     * true  : la méthode retourne déjà une chaîne JSON → écrite telle quelle.
     * false : le framework sérialise l'objet retourné en JSON.
     */
    boolean alreadyJson() default false;
}