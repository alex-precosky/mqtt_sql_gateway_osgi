package org.eclipse.kura.alexsensors.mqtt_sql_gateway_osgi;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;
import org.eclipse.kura.cloud.CloudService;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentException;
import org.eclipse.kura.cloud.CloudClient;
import org.eclipse.kura.cloud.CloudClientListener;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.message.KuraPayload;
import org.eclipse.kura.crypto.CryptoService;

import org.eclipse.kura.configuration.Password;

public class MQTT_SQL_Gateway implements ConfigurableComponent, CloudClientListener {

    private static final Logger s_logger = LoggerFactory.getLogger(MQTT_SQL_Gateway.class);
    private static final String APP_ID = "org.eclipse.kura.alexsensors.MQTT_SQL_Gateway";

    private static final String MQTT_APP_ID = "alexsensors";

    private static final String DB_HOSTNAME_PROP_NAME = "mysql.hostname";
    private static final String DB_DATABASE_PROP_NAME = "mysql.database";
    private static final String DB_USER_PROP_NAME = "mysql.user";
    private static final String DB_PASSWORD_PROP_NAME = "mysql.password";

    private Map<String, Object> m_properties;

    private CloudService m_cloudService;
    private CloudClient m_cloudClient;

    private CryptoService m_cryptoService;

    // Collect one each of a temperature and a voltage then send them to the MySQL server
    private Float m_tempeature = null;
    private Float m_battery_voltage = null;

    private static final String TEMPERATURE_TOPIC = "fridge/temperature";
    private static final String BAT_VOLTAGE_TOPIC = "fridge/batVoltage";

    public void setCloudService(CloudService cloudService) {
        this.m_cloudService = cloudService;
        s_logger.info("Cloud service set!");
    }

    public void unsetCloudService(CloudService cloudService) {
        this.m_cloudService = null;
    }

    public void setCryptoService(CryptoService cryptoService) {
        this.m_cryptoService = cryptoService;
        s_logger.info("Crypto service set!");
    }

    public void unsetCryptoService(CloudService cryptoService) {
        this.m_cryptoService = null;
    }

    private Connection buildConnection() throws SQLException {
        String hostname = (String) this.m_properties.get(DB_HOSTNAME_PROP_NAME);
        String database = (String) this.m_properties.get(DB_DATABASE_PROP_NAME);
        String user = (String) this.m_properties.get(DB_USER_PROP_NAME);

        String encrypted_password = (String) this.m_properties.get(DB_PASSWORD_PROP_NAME);

        char[] decryptedPasswordBytes = null;
            String decryptedPassword = null;

            try {
                decryptedPasswordBytes = m_cryptoService.decryptAes(encrypted_password.toCharArray());
            }
            catch (Exception e) {
                s_logger.error("Exception using crypto service", e);
            }

            decryptedPassword = new String(decryptedPasswordBytes);

            String ConnectionURL = "jdbc:mysql://" + hostname + ":3306/" + database + "?user=" + user + "&password="
                + decryptedPassword + "&serverTimezone=UTC";

            return DriverManager.getConnection(ConnectionURL);
    }

    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        s_logger.info("Starting");
        m_properties = new HashMap<String, Object>();

        try {
            s_logger.info("Getting CloudClient for {}...", APP_ID);
            if (this.m_cloudService != null ) {
                m_cloudClient = m_cloudService.newCloudClient("alexsensors");
                this.m_cloudClient.addCloudClientListener(this);
                doUpdate(properties);
            }
        } catch (Exception e) {
            s_logger.error("Error during component activation", e);
            throw new ComponentException(e);
        }

        // Load the Mysql database driver
        try {
            Class.forName("com.mysql.cj.jdbc.Driver").newInstance();
        } catch (Exception ex) {
            s_logger.error("Error loading jdbc driver", ex);
        }

        // Create the Mysql dataase connection to test connectivity on startup, but close the connection right away
        try {
            Connection my_connection = buildConnection();
            my_connection.close();
        } catch (SQLException ex) {
            // handle any errors
            s_logger.error("SQLException: ", ex);
        }

        s_logger.info("Started");
    }

    protected void deactivate(ComponentContext componentContext) {
        m_cloudClient.release();

        s_logger.info("Bundle " + APP_ID + " has stopped!");
    }

    public void updated(Map<String, Object> properties) {
        s_logger.info("Updating MQTT_SQL_Gateway...");

        // log the new properties
        if (properties != null && !properties.isEmpty()) {
            Iterator<Entry<String, Object>> it = properties.entrySet().iterator();
            while (it.hasNext()) {
                Entry<String, Object> entry = it.next();
                s_logger.info("New property - " + entry.getKey() + " = " + entry.getValue() + " of type "
                        + entry.getValue().getClass().toString());
            }
        }

        doUpdate(properties);
        s_logger.info("Updating MQTT_SQL_Gateway...Done.");
    }

    // Called by the updated and activate events
    private void doUpdate(Map<String, Object> properties) {
        try {
            for (String s : properties.keySet()) {
                s_logger.info("Update - "+s+": "+properties.get(s));
            }

            // store the properties
            m_properties.clear();
            m_properties.putAll(properties);
        } catch (Throwable t) {
            s_logger.error("Unexpected Throwable", t);
        }
    }

    private void do_transmit(Float temperature, Float battery_voltage)
    {
        // get time stamp in local time
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());

        try (Connection my_connection = buildConnection()) {
            String qryStr = "INSERT INTO fridge (time, temperature, batteryVoltage) VALUES (?, ?, ?)";
            PreparedStatement st = my_connection.prepareStatement(qryStr);
            st.setTimestamp(1, timestamp);
            st.setFloat(2, temperature);
            st.setFloat(3, battery_voltage);

            st.executeUpdate();
            my_connection.close();
            s_logger.info("Inserted!");
        }
        catch (SQLException ex) {
            s_logger.error("SQLException while inserting: ", ex);
        }
    }

    @Override
    public void onConnectionEstablished() {
        s_logger.info("Connection established. Subscribing to MQTT topics...");
        try {
            m_cloudClient.subscribe(TEMPERATURE_TOPIC, 0);
            m_cloudClient.subscribe(BAT_VOLTAGE_TOPIC, 0);
        }
        catch (Exception ex) {
            s_logger.error("Exception subscribing to MQTT topics: ", ex);
        }
    }

    @Override
    public void onMessagePublished(int messageId, String appTopic) {
        s_logger.trace("Published message with ID: {} on application topic: {}", messageId, appTopic);
    }

    @Override
    public void onControlMessageArrived(String deviceId, String appTopic, KuraPayload msg, int qos, boolean retain) {
        s_logger.trace("Control message arrived on assetId: {} and semantic topic: {}", deviceId, appTopic);
    }

    @Override
    public void onMessageArrived(String deviceId, String appTopic, KuraPayload msg, int qos, boolean retain) {
        s_logger.trace("Message arrived on assetId: {} and semantic topic: {}", deviceId, appTopic);

        if (appTopic.equals(TEMPERATURE_TOPIC)) {
            m_tempeature = Float.parseFloat( new String(msg.getBody()) );
            s_logger.trace("Temperature received: {}", m_tempeature);
        } else if (appTopic.equals(BAT_VOLTAGE_TOPIC)) {
            m_battery_voltage = Float.parseFloat( new String(msg.getBody()) );
            s_logger.trace("Battery voltage received: {}", m_battery_voltage);
        }

        if (m_tempeature != null && m_battery_voltage != null) {
            s_logger.trace("Transmitting");
            do_transmit(m_tempeature, m_battery_voltage);

            m_tempeature = null;
            m_battery_voltage = null;
        }
    }

    @Override
    public void onConnectionLost() {
        s_logger.error("Connection lost!");
    }

    @Override
    public void onMessageConfirmed(int messageId, String appTopic) {
        s_logger.trace("Confirmed message with ID: {} on application topic: {}", messageId, appTopic);
    }
}
