#!/bin/bash

# Ce script utilise l'API de LaTeX Online pour compiler un document LaTeX en PDF

# Vérifier si le fichier existe
if [ ! -f "decentralized_coordination.tex" ]; then
    echo "Erreur: Le fichier decentralized_coordination.tex n'existe pas"
    exit 1
fi

echo "Compilation du document LaTeX en PDF..."
echo "Cela peut prendre quelques instants..."

# Lire le contenu du fichier LaTeX
LATEX_CONTENT=$(cat decentralized_coordination.tex)

# Encoder le contenu pour l'URL
ENCODED_CONTENT=$(echo "$LATEX_CONTENT" | jq -s -R -r @uri)

# Envoyer la requête à LaTeX Online
curl -X POST "https://latexonline.cc/compile" \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -d "text=$ENCODED_CONTENT&force=true" \
     -o decentralized_coordination.pdf

# Vérifier si le PDF a été créé
if [ -f "decentralized_coordination.pdf" ]; then
    echo "Le PDF a été créé avec succès: decentralized_coordination.pdf"
    exit 0
else
    echo "Erreur: La compilation a échoué"
    exit 1
fi
