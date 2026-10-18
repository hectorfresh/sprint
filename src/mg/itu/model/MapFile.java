package mg.itu.model;

import java.util.Arrays;

/**
 * Représente un fichier reçu depuis un formulaire multipart.
 *
 * nomFichier est le nom sans extension ; extension est stockée sans le point.
 * Exemple : contrat.pdf -> nomFichier="contrat", extension="pdf".
 * cheminStockage est renseigné automatiquement après l'enregistrement local.
 */
public class MapFile {

    private String nomFichier;
    private String extension;
    private byte[] bytes;
    private String cheminStockage;

    public MapFile() {
        this.bytes = new byte[0];
    }

    public MapFile(String nomFichier, String extension, byte[] bytes) {
        this.nomFichier = nomFichier;
        this.extension = extension;
        setBytes(bytes);
    }

    public String getNomFichier() {
        return nomFichier;
    }

    public void setNomFichier(String nomFichier) {
        this.nomFichier = nomFichier;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public byte[] getBytes() {
        return bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
    }

    /** Chemin absolu du fichier enregistré localement, ou null avant stockage. */
    public String getCheminStockage() {
        return cheminStockage;
    }

    public void setCheminStockage(String cheminStockage) {
        this.cheminStockage = cheminStockage;
    }

    public boolean isEmpty() {
        return nomFichier == null || nomFichier.trim().isEmpty();
    }
}
