#!/bin/bash

# Define the classpath with the required JAR files
CLASSPATH="/Users/oussamaguelfaa/Downloads/ini4j-0.5.1.jar:/Users/oussamaguelfaa/Downloads/maqitSimulator (1).jar:."

# Create bin directory if it doesn't exist
mkdir -p bin

# Copy JAR files to a local lib directory for easier access
mkdir -p lib
cp "/Users/oussamaguelfaa/Downloads/ini4j-0.5.1.jar" lib/
cp "/Users/oussamaguelfaa/Downloads/maqitSimulator (1).jar" "lib/maqitSimulator.jar"

# Update classpath to use local lib directory
CLASSPATH="lib/ini4j-0.5.1.jar:lib/maqitSimulator.jar:."

# Compile all Java files
echo "Compiling Java files..."
"/Users/oussamaguelfaa/Library/Application Support/Code/User/globalStorage/pleiades.java-extension-pack-jdk/java/17/bin/javac" -source 17 -target 17 -d bin -cp "$CLASSPATH" src/Simulator/*.java

# Check if compilation was successful
if [ $? -eq 0 ]; then
    echo "Compilation successful. Running the simulation..."
    # Run the simulation
    "/Users/oussamaguelfaa/Library/Application Support/Code/User/globalStorage/pleiades.java-extension-pack-jdk/java/17/bin/java" -cp "$CLASSPATH:bin" Simulator.MySimFactory
else
    echo "Compilation failed."
fi
