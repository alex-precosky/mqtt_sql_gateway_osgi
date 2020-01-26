# mqtt_sql_gateway_osgi

This is a module for Eclipse Kura 3.1 for putting MQTT values into a MySQL database.    It's probably not for general use as-is since it's
pretty specific to my application, but would be a handy starting point for anyone interested in using Kura as a gateway in their IoT application
where they want to put values in a MySQL or other database.

It implements the ConfigurableComponent interface, letting you set it up through the Kura web interface.

MySQL passwords are stored encrypted, using the Kura CryptoService to decrypt them.

It uses the Kura CloudService to subscribe to the MQTT topics that it listens on.

## Eclipse Kura
Kura is a Java framework for IoT gateways. https://www.eclipse.org/kura/index.php

# Requirements
- Eclipse Kura 3.1+ (tested most recently with Kura 4.1)
- Connector/J (JDBC driver for MySQL)
- MQTT Server
- Periodic sensor values coming from a serial port

# Setup
## Connector/J

Download and extract Connector/J from the Oracle website. It will contain a .jar file which is what we need.

In the 'target-definition' project in you Eclipse workspace, open the kura target, and add the location of the .jar file for Connector/J to the list of locations
where plugins will be collected from.

In the org.eclipse.kura.alexsensors.mqtt_sql_gateway_osgi Eclipse project under META-INF/MANIFEST.MF, the list of imported packages may need to be updated to match the version of Connector/J that was downloaded.

Similarly, in resoruces/dp/mqtt_sql_gateway_osgi.dpp, the list of bundles included in the package may need to have the .jar file for Connector/J updated to match the one
downloaded.

# Building

## OSGi .jar file bundle for testing
In Eclipse, right click on the project and choose Export. Select Plug-in Development - Deployable plug-ins and fragments. Select the project and click Finish.

# Deployment
The .jar file bundle of the project needs to be built first, as above. Then, find the .dpp file in the Eclipse project structure, under resources/dp. Open it and it will update its configuration to use the newly built .jar file. Right click on the .dpp file and choose Quick Build. The resulting .dp file can be permanantly deployed by copying it to the gate way under the proper directory, which for my Kura installation was /opt/eclipse/kura_3.1.0-SNAPSHOT_raspberry-pi-2/kura/packages