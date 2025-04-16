# Instructions pour compiler le document LaTeX en PDF

Puisque nous ne pouvons pas installer LaTeX directement dans cet environnement, voici comment compiler le document en PDF:

## Option 1: Utiliser Overleaf (Recommandé)

1. Allez sur [Overleaf](https://www.overleaf.com/)
2. Créez un compte gratuit ou connectez-vous
3. Créez un nouveau projet
4. Téléchargez le fichier `decentralized_coordination.tex`
5. Cliquez sur "Compiler" pour générer le PDF

## Option 2: Installer LaTeX localement

### Pour macOS:
```
brew install --cask basictex
```

Après l'installation, vous devrez peut-être ajouter les binaires LaTeX à votre PATH:
```
export PATH=$PATH:/Library/TeX/texbin
```

Ensuite, installez les packages LaTeX nécessaires:
```
sudo tlmgr update --self
sudo tlmgr install amsmath amsfonts amssymb graphicx hyperref xcolor listings algorithm algpseudocode tikz float
```

### Pour Windows:
1. Téléchargez et installez MiKTeX depuis https://miktex.org/download
2. Installez TeXworks ou un autre éditeur LaTeX

### Pour Linux:
```
sudo apt-get install texlive-base texlive-latex-recommended texlive-latex-extra
```

Après l'installation, vous pouvez compiler le document avec:
```
pdflatex decentralized_coordination.tex
```

## Option 3: Utiliser un service en ligne

Si vous ne voulez pas installer LaTeX, vous pouvez utiliser un service en ligne comme:

1. [LaTeX Base](https://latexbase.com/)
2. [LaTeX Online](https://latexonline.cc/)
3. [Papeeria](https://papeeria.com/)

Il suffit de copier le contenu du fichier `decentralized_coordination.tex` et de le coller dans l'éditeur en ligne, puis de cliquer sur "Compiler".
