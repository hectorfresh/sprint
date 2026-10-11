#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

# --- Sélection explicite d'un JDK compatible (même principe que le script précédent) ---
# Par défaut, on utilise Java 17. JAVA17_HOME permet de préciser un autre chemin si besoin.
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
  # Chercher d'abord Java 17, puis un JDK plus récent (qui comprend aussi --release 17).
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
  echo "Ou indiquez un chemin : JAVA17_HOME=/chemin/vers/jdk-17 ./build.sh" >&2
  exit 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
echo "☕ $(javac -version 2>&1) (JAVA_HOME=$JAVA_HOME)"

# Configuration
SRC_DIR="src"
LIB_DIR="lib"
BUILD_DIR="build"
OUTPUT_JAR="framework.jar"
JAVA_RELEASE="${JAVA_RELEASE:-17}"

# Trouver le JAR Servlet (il ne doit pas être inclus dans le JAR du framework)
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

echo "Compilation en cours (Java release $JAVA_RELEASE)..."
mapfile -t SOURCES < <(find "$SRC_DIR" -name '*.java' -print | sort)
if [[ ${#SOURCES[@]} -eq 0 ]]; then
  echo "Erreur : aucun fichier Java trouvé dans $SRC_DIR" >&2
  exit 1
fi
javac --release "$JAVA_RELEASE" -cp "$SERVLET_JAR" -d "$BUILD_DIR/classes" "${SOURCES[@]}"

echo "Compilation réussie !"
echo "Création du JAR..."
jar cvf "$OUTPUT_JAR" -C "$BUILD_DIR/classes" .
echo "✅ JAR créé : $OUTPUT_JAR"
echo -e "\nContenu du JAR :"
jar tf "$OUTPUT_JAR" | head -20
