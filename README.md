# mqtt_sql_gateway_osgi

This is a module for Eclipse Kura 3.1 for putting MQTT values into a MySQL database.    It's probably not for general use as-is since it's
pretty specific to my application, but would be a handy starting point for anyone interested in using Kura as a gateway in their IoT application
where they want to put values in a MySQL or other database.

It implements the ConfigurableComponent interface, letting you set it up through the Kura web interface.

MySQL passwords are stored encrypted, using the Kura CryptoService to decrypt them.

It uses the Kura CloudService to subscribe to the MQTT topics that it listens on.

## Eclipse Kura
Kura is a Java framework for IoT gateways. https://www.eclipse.org/kura/index.php

