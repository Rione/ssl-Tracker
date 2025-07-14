# SSL Vision Tracker Output

This is a minimal SSL Vision tracker implementation that outputs tracking data on port 10010.
Based on TIGERs Mannheim AutoReferee framework, this version removes all GUI components, 
referee functionality, and outputs only vision tracking data.

## Features
- Receives SSL Vision data from port 10006
- Processes and tracks ball and robot positions
- Outputs standardized tracking data on port 10010 (multicast address 224.5.23.2)

## System Requirements
 * Java JDK (recent LTS version, exact required version can be
  found [here](buildSrc/src/main/groovy/sumatra.java.gradle))
 * Internet connection
 * no limitations on OS known

## Build
First, make sure you have cloned the GIT repository. The ZIP-file from GitHub does not work, as the build relies on Git tags.

Run `./build.sh` or `./build.bat`, depending on your system platform.

## Run
Run `./run.sh` or `run.bat`, depending on your system platform.

You can pass `-h` to get the available arguments.

Available options:
- `-va, --visionAddress`: Set vision input address:port (default: 224.5.23.2:10006)
- `-ta, --trackerAddress`: Set tracker output address:port (default: 224.5.23.2:10010)

Example:
```shell
./run.sh -va 224.5.23.2:10006 -ta 224.5.23.2:10010
```

## Docker
You can run it through docker as well:

```shell
docker run --net host tigersmannheim/ssl-tracker
```

## IntelliJ
IntelliJ reads the Gradle configuration and can use Gradle to perform the build.
Make sure to configure Gradle for build and tests under Build, Execution, Deployment -> Build Tools -> Gradle.

# Configuration

The tracker uses minimal configuration in `config/moduli/tracker-only.xml` that only includes:
- SSL Vision camera input (port 10006)
- Geometry processing
- Vision filtering
- World state prediction
- Tracker output (port 10010)

# Original Project

This is based on TIGERs Mannheim AutoReferee:
- Homepage: https://www.tigers-mannheim.de  
- Mail: info@tigers-mannheim.de
