# Hello Simulation

This is a simulation project using the maqitSimulator framework.

## Requirements

- Java 17 (specifically the version at `/Users/oussamaguelfaa/Library/Application Support/Code/User/globalStorage/pleiades.java-extension-pack-jdk/java/17`)
- Required JAR files:
  - `ini4j-0.5.1.jar`
  - `maqitSimulator.jar`

## How to Run the Project

### Option 1: Using VS Code

1. Open the project in VS Code
2. Press F5 or click the Run button
3. The simulation will start automatically

### Option 2: Using the run.sh Script

1. Open a terminal in the project directory
2. Run the following command:
   ```
   ./run.sh
   ```
3. The script will compile the project and run the simulation

## Project Structure

- `src/Simulator/`: Contains all the Java source files
- `parameters/`: Contains configuration files
  - `configuration.ini`: General simulation parameters
  - `environment.ini`: Environment configuration
- `lib/`: Contains required libraries (created by the run script)
  - `ini4j-0.5.1.jar`: INI file parser
  - `maqitSimulator.jar`: Simulation framework

## Troubleshooting

If you encounter any issues:

1. Make sure the required JAR files exist in the Downloads folder:
   - `/Users/oussamaguelfaa/Downloads/ini4j-0.5.1.jar`
   - `/Users/oussamaguelfaa/Downloads/maqitSimulator (1).jar`

2. If the JAR files are in a different location, update the paths in:
   - `run.sh`
   - `.vscode/launch.json`
   - `.vscode/tasks.json`
   - `.vscode/settings.json`

3. If you encounter a "UnsupportedClassVersionError", make sure you're using Java 17:
   - The project is configured to use Java 17 from the VS Code Java extension pack
   - Both compilation and execution are set to use this specific Java version
