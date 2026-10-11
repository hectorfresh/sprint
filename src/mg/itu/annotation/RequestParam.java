package mg.itu.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface RequestParam {
    /** Nom du paramètre HTTP à récupérer. */
    String value();

    /** Valeur par défaut si le paramètre est absent. */
    String defaultValue() default "";

    /** true si le paramètre est obligatoire. */
    boolean required() default true;
}