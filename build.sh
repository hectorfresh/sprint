#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

# Sélection d'un JDK compatible avec --release 17.
JAVA_HOME="${JAVA17_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"

supports_release_17() {
    local candidate="$1"
    [[ -x "$candidate/bin/javac" ]] || return 1
    local version major
    version=$("$candidate/bin/javac" -version 2>&1 | awk '{print $2}')
    major="${version%%.*}"
    [[ "$major" =~ ^[0-9]+$ ]] && (( major >= 17 ))
}

if ! supports_release_17 "$JAVA_HOME"; then
    JAVA_HOME=""
    for candidate in /usr/lib/jvm/java-17-* /usr/lib/jvm/jdk-17* /usr/lib/jvm/java-* /usr/lib/jvm/jdk-*; do
        [[ -d "$candidate" ]] || continue
        if supports_release_17 "$candidate"; then
            JAVA_HOME="$candidate"
            break
        fi
    done
fi

if ! supports_release_17 "$JAVA_HOME"; then
    echo "Erreur : aucun JDK 17 ou plus récent trouvé." >&2
    echo "Installez-le avec : sudo apt install openjdk-17-jdk" >&2
    echo "Ou indiquez le chemin : JAVA17_HOME=/chemin/vers/jdk-17 ./build.sh" >&2
    exit 1
fi

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
echo "Java utilisé : $(javac -version 2>&1) (JAVA_HOME=$JAVA_HOME)"

SRC_DIR="src"
LIB_DIR="lib"
BUILD_DIR="build"
OUTPUT_JAR="framework.jar"
JAVA_RELEASE="${JAVA_RELEASE:-17}"

SERVLET_JAR=$(find "$LIB_DIR" -maxdepth 1 -name "jakarta.servlet-api-*.jar" -print -quit)
if [[ -z "$SERVLET_JAR" ]]; then
    SERVLET_JAR=$(find "$LIB_DIR" -maxdepth 1 -name "*servlet*.jar" -print -quit)
fi
if [[ -z "$SERVLET_JAR" ]]; then
    echo "Erreur : aucun JAR Servlet trouvé dans $LIB_DIR" >&2
    exit 1
fi

echo "Servlet JAR trouvé : $SERVLET_JAR"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes"
mapfile -t SOURCES < <(find "$SRC_DIR" -name '*.java' -print | sort)
if [[ ${#SOURCES[@]} -eq 0 ]]; then
    echo "Erreur : aucun fichier Java trouvé dans $SRC_DIR" >&2
    exit 1
fi

echo "Compilation en cours (Java release $JAVA_RELEASE)..."
javac --release "$JAVA_RELEASE" -cp "$SERVLET_JAR" -d "$BUILD_DIR/classes" "${SOURCES[@]}"

echo "Compilation réussie !"
jar cf "$OUTPUT_JAR" -C "$BUILD_DIR/classes" .

# Fournit une configuration par défaut dans le JAR. Une application peut la
# remplacer en plaçant son application.properties dans WEB-INF/classes.
if [[ -f application.properties ]]; then
    jar uf "$OUTPUT_JAR" application.properties
fi

echo "JAR créé : $OUTPUT_JAR"
echo "Contenu principal du JAR :"
jar tf "$OUTPUT_JAR" | head -25
