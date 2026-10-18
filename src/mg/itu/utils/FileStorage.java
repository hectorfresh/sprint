package mg.itu.utils;

import jakarta.servlet.ServletContext;
import mg.itu.model.MapFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/** Enregistrement local des fichiers téléversés. */
public final class FileStorage {

    private static final String CONFIG_FILE = "application.properties";
    private static final String UPLOAD_DIRECTORY_PROPERTY = "upload.directory";
    private static final String DEFAULT_UPLOAD_DIRECTORY = "uploads";

    private FileStorage() {
    }

    /**
     * Enregistre un MapFile dans le dossier configuré et retourne le chemin absolu.
     * Les doublons ne remplacent jamais un fichier déjà présent : un suffixe est ajouté.
     */
    public static String store(MapFile file, ServletContext context) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        Properties properties = loadProperties(context);
        Path directory = resolveDirectory(
                properties.getProperty(UPLOAD_DIRECTORY_PROPERTY, DEFAULT_UPLOAD_DIRECTORY));
        Files.createDirectories(directory);

        String baseName = sanitizeBaseName(file.getNomFichier());
        String extension = sanitizeExtension(file.getExtension());
        if (baseName.isEmpty()) {
            baseName = "fichier";
        }
        String suffix = extension.isEmpty() ? "" : "." + extension;
        byte[] bytes = file.getBytes();

        int index = 0;
        while (true) {
            String candidateName = baseName + (index == 0 ? "" : "-" + index) + suffix;
            Path target = directory.resolve(candidateName).normalize();

            // Le nom généré ne doit jamais pouvoir sortir du dossier configuré.
            if (!target.getParent().equals(directory)) {
                throw new IOException("Nom de fichier non valide : " + candidateName);
            }

            try {
                Files.write(target, bytes, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                String absolutePath = target.toAbsolutePath().toString();
                file.setCheminStockage(absolutePath);
                if (context != null) {
                    context.log("[Framework] Fichier téléversé enregistré : " + absolutePath);
                }
                return absolutePath;
            } catch (FileAlreadyExistsException alreadyExists) {
                index++;
            }
        }
    }

    private static Properties loadProperties(ServletContext context) throws IOException {
        Properties properties = new Properties();

        if (context != null) {
            // Priorité à la configuration propre à l'application déployée.
            if (loadFromServletContext(context, "/WEB-INF/classes/" + CONFIG_FILE, properties)) {
                return properties;
            }
            if (loadFromServletContext(context, "/WEB-INF/" + CONFIG_FILE, properties)) {
                return properties;
            }
        }

        // Le classloader de l'application peut trouver WEB-INF/classes ou un fichier du JAR.
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader != null) {
            try (InputStream input = classLoader.getResourceAsStream(CONFIG_FILE)) {
                if (input != null) {
                    properties.load(input);
                    return properties;
                }
            }
        }

        // Pratique pour les exécutions locales hors conteneur Web.
        Path localConfig = Paths.get(CONFIG_FILE);
        if (Files.isRegularFile(localConfig)) {
            try (InputStream input = Files.newInputStream(localConfig)) {
                properties.load(input);
            }
        }

        return properties;
    }

    private static boolean loadFromServletContext(ServletContext context,
            String resourcePath, Properties properties) throws IOException {
        try (InputStream input = context.getResourceAsStream(resourcePath)) {
            if (input == null) {
                return false;
            }
            properties.load(input);
            return true;
        }
    }

    private static Path resolveDirectory(String configuredDirectory) {
        String value = configuredDirectory == null || configuredDirectory.trim().isEmpty()
                ? DEFAULT_UPLOAD_DIRECTORY : configuredDirectory.trim();
        Path directory = Paths.get(value);

        if (!directory.isAbsolute()) {
            // Un chemin relatif est placé sous le répertoire de Tomcat pour survivre
            // au remplacement du WAR. Hors Tomcat, user.dir sert de répertoire de base.
            String base = System.getProperty("catalina.base");
            if (base == null || base.trim().isEmpty()) {
                base = System.getProperty("user.dir", ".");
            }
            directory = Paths.get(base).resolve(directory);
        }

        return directory.toAbsolutePath().normalize();
    }

    private static String sanitizeBaseName(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash >= 0) {
            normalized = normalized.substring(lastSlash + 1);
        }
        // Le nom reçu est déjà séparé de l'extension par ParamBinder.
        normalized = normalized.replaceAll("[^\\p{L}\\p{N}._ -]", "_").trim();
        while (normalized.contains("..")) {
            normalized = normalized.replace("..", ".");
        }
        return normalized;
    }

    private static String sanitizeExtension(String value) {
        if (value == null) {
            return "";
        }
        String extension = value.trim().replaceFirst("^\\.", "");
        extension = extension.replaceAll("[^A-Za-z0-9]", "").toLowerCase(java.util.Locale.ROOT);
        return extension;
    }
}
