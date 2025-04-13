# AMR Simulation with Battery Management

## Overview

This project simulates Autonomous Mobile Robots (AMRs) in a warehouse-like environment, featuring package delivery, transit zones, and battery management. The simulation demonstrates how robots can efficiently transport packages from start zones to goal destinations while managing their battery levels and utilizing charging stations.

## Key Features

### Robot Types
- **Transit Robots**: Advanced robots that can use transit zones and manage battery levels
- **Basic Robots**: Simpler robots that deliver packages directly to destinations
- **Workers**: Mobile obstacles that move randomly in the environment

### Battery Management System
- **Battery Levels**: Robots track their battery percentage (0-100%)
- **Visual Indicators**: Robot colors change based on battery level:
  - Green: Excellent (>70%)
  - Orange: Medium (30-70%)
  - Light Red: Low (20-30%)
  - Medium Red: Very Low (10-20%)
  - Dark Red: Critical (<10%)
- **Charging Stations**: Located near transit zones for robots to recharge
- **Battery Consumption**: Different actions consume varying amounts of battery:
  - Movement: 0.5% per step
  - Package pickup/deposit: 2%
- **Charging Rate**: 5% per simulation step

### Communication Between Robots
- **Low Battery Alerts**: Robots broadcast when their battery is low
- **Help Requests**: Robots can request assistance from others
- **Transit Zone Status**: Robots share information about transit zone availability

### Environment Components
- **Start Zones**: Where packages are generated (A1, A2, A3)
- **Transit Zones**: Intermediate storage areas for packages
- **Goal Destinations**: Final delivery locations (Z1, Z2)
- **Exit Zones**: Areas where robots can exit after delivery
- **Obstacles**: Static barriers in the environment

## Requirements

- Java 17
- Required JAR files:
  - `ini4j-0.5.1.jar`: INI file parser
  - `maqitSimulator.jar`: Simulation framework

## How to Run the Project

### Option 1: Using VS Code (Recommended)

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
  - `MySimFactory.java`: Main simulation controller
  - `MyRobot.java`: Basic robot implementation
  - `MyTransitRobot.java`: Advanced robot with battery management
  - `Worker.java`: Mobile obstacles
  - `SimpleMessage.java`: Communication between robots

- `parameters/`: Contains configuration files
  - `configuration.ini`: General simulation parameters (robot count, display settings)
  - `environment.ini`: Environment configuration (obstacles, zones, goals)

- `lib/`: Contains required libraries (created by the run script)

## Simulation Workflow

1. **Package Generation**: Packages are created at start zones with random destinations
2. **Package Collection**: Robots pick up packages from start zones
3. **Transport Options**:
   - Direct delivery to goal destination
   - Deposit in transit zone for later pickup by another robot
4. **Battery Management**:
   - Robots monitor battery levels during operation
   - When battery is low, robots seek charging stations
   - Robots communicate battery status to others
5. **Delivery Completion**: Robots deliver packages to goal destinations and report success

## Configuration

You can modify the simulation by editing the INI files in the `parameters` directory:

- **Robot Count**: Change `robot = 20` in `configuration.ini`
- **Environment Size**: Adjust `rows` and `columns` in `configuration.ini`
- **Obstacle Positions**: Edit the `obstacles` section in `environment.ini`
- **Transit Zone Capacity**: Modify the capacity value in `transitZones` section of `environment.ini`

## Troubleshooting

If you encounter any issues:

1. Make sure the required JAR files exist in the correct location
2. If the JAR files are in a different location, update the paths in configuration files
3. If you encounter a "UnsupportedClassVersionError", make sure you're using Java 17
4. Check the console output for detailed error messages and battery status logs
