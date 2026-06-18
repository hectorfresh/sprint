#!/bin/bash

# Configuration
SRC_DIR="src"
LIB_DIR="lib"
BUILD_DIR="build"
OUTPUT_JAR="framework.jar"

# Trouver automatiquement le servlet JAR
SERVLET_JAR=$(find $LIB_DIR -name "*.jar" | head -1)

if [ -z "$SERVLET_JAR" ]; then
    echo "Erreur: Aucun JAR servlet trouvé dans $LIB_DIR"
    exit 1
fi

echo "Servlet JAR trouvé : $SERVLET_JAR"

# Nettoyer
rm -rf $BUILD_DIR
mkdir -p $BUILD_DIR/classes

# Compiler
echo "Compilation en cours..."
javac -cp "$SERVLET_JAR" -d $BUILD_DIR/classes $(find $SRC_DIR -name "*.java")

if [ $? -eq 0 ]; then
    echo "Compilation réussie !"
    
    # Créer le JAR
    echo "Création du JAR..."
    jar cvf $OUTPUT_JAR -C $BUILD_DIR/classes .
    
    echo "✅ JAR créé : $OUTPUT_JAR"
    
    # Vérifier le contenu (sans les libs)
    echo -e "\nContenu du JAR :"
    jar tf $OUTPUT_JAR | head -20
else
    echo "❌ Erreur de compilation"
    exit 1
fi