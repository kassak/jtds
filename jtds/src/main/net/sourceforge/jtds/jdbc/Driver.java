// jTDS JDBC Driver for Microsoft SQL Server
// Copyright (C) 2004 The jTDS Project
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
package net.sourceforge.jtds.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

/**
 * jTDS implementation of the java.sql.Driver interface.
 * <p>
 * Implementation note:
 * <ol>
 * <li>Property text names and descriptions are loaded from an external file resource.
 *     This allows the actual names and descriptions to be changed or localised without
 *     impacting this code.
 * <li>The way in which the URL is parsed and converted to properties is rather
 *     different from the original jTDS Driver class.
 *     See parseURL and Connection.unpackProperties methods for more detail.
 * </ol>
 * @see java.sql.Driver
 * @author Brian Heineman
 * @author Mike Hutchinson
 * @author Alin Sinpalean
 * @version $Id: Driver.java,v 1.34 2004-08-14 16:43:27 bheineman Exp $
 */
public class Driver implements java.sql.Driver {
    private static String driverPrefix = "jdbc:jtds:";
    static final int MAJOR_VERSION = 0;
    static final int MINOR_VERSION = 9;
    public static final boolean JDBC3 =
            "1.4".compareTo(System.getProperty("java.specification.version")) <= 0;
    /** TDS 4.2 protocol. */
    public static final int TDS42 = 1;
    /** TDS 5.0 protocol. */
    public static final int TDS50 = 2;
    /** TDS 7.0 protocol. */
    public static final int TDS70 = 3;
    /** TDS 8.0 protocol. */
    public static final int TDS80 = 4;
    /** Microsoft SQL Server. */
    public static final int SQLSERVER = 1;
    /** Sybase ASE. */
    public static final int SYBASE = 2;

    static {
        try {
            // Register this with the DriverManager
            DriverManager.registerDriver(new Driver());
        } catch (SQLException e) {
        }
    }

    public int getMajorVersion() {
        return MAJOR_VERSION;
    }

    public int getMinorVersion() {
        return MINOR_VERSION;
    }

    /**
     * Returns the driver version.
     * <p>
     * Per [908906] 0.7: Static Version information, please.
     *
     * @return the driver version
     */
    public static final String getVersion() {
        return "jTDS " + MAJOR_VERSION + "." + MINOR_VERSION;
    }

    /**
     * Returns the string form of the object.
     * <p>
     * Per [887120] DriverVersion.getDriverVersion(); this will return a short
     * version name.
     * <p>
     * Added back to driver per [1006449] 0.9rc1: Driver version broken
     *
     * @return the driver version
     */
    public String toString() {
        return getVersion();
    }

    public boolean jdbcCompliant() {
        return false;
    }

    public boolean acceptsURL(String url) throws SQLException {
        if (url == null) {
            return false;
        }
        
        return url.toLowerCase().startsWith(driverPrefix);
    }

    public Connection connect(String url, Properties info)
        throws SQLException  {
        if (url == null || !url.toLowerCase().startsWith(driverPrefix)) {
            return null;
        }

        Properties props = parseURL(url, info);

        if (props == null) {
            throw new SQLException(Messages.get("error.driver.badurl", url), "08001");
        }

        if (props.getProperty(Messages.get("prop.logintimeout")) == null) {
            props.setProperty(Messages.get("prop.logintimeout"), Integer.toString(DriverManager.getLoginTimeout()));
        }

        if (JDBC3) {
            return new ConnectionJDBC3(url, props);
        }

        return new ConnectionJDBC2(url, props);
    }

    public DriverPropertyInfo[] getPropertyInfo(final String url, final Properties props)
            throws SQLException {

        final Properties parsedProps = parseURL(url, (props == null ? new Properties() : props));

        if (parsedProps == null) {
            throw new SQLException(
                        Messages.get("error.driver.badurl", url), "08001");
        }

        final Map propertyMap = new HashMap();
        final Map descriptionMap = new HashMap();
        Messages.loadDriverProperties(propertyMap, descriptionMap);

        final Map choicesMap = createChoicesMap();
        final Map requiredTrueMap = createRequiredTrueMap();

        final DriverPropertyInfo[] dpi = new DriverPropertyInfo[propertyMap.size()];
        final Iterator iterator = propertyMap.keySet().iterator();
        for (int i = 0; iterator.hasNext(); i++) {

            final String key = (String) iterator.next();
            final String name = (String) propertyMap.get(key);

            final DriverPropertyInfo info = new DriverPropertyInfo(name, parsedProps.getProperty(name));
            info.description = (String) descriptionMap.get(key);

            if (requiredTrueMap.containsKey(name)) {
                info.required = true;
            }
            else {
                info.required = false;
            }

            if (choicesMap.containsKey(name)) {
                info.choices = (String[]) choicesMap.get(name);
            }

            dpi[i] = info;
        }

        return dpi;
    }

    /**
     * Creates a map of driver properties whose <code>choices</code>
     * field should be set when calling
     * {@link #getPropertyInfo(String, Properties)}.
     * <p/>
     * The values in the map are the <code>String[]</code> objects
     * that should be set to the <code>choices</code> field.
     * 
     * @return The map of {@link DriverPropertyInfo} objects whose
     *         <code>choices</code> should be set.
     */
    private Map createChoicesMap() {

        final HashMap choicesMap = new HashMap();

        final String[] booleanChoices = new String[]{"true", "false"};
        choicesMap.put(Messages.get("prop.lastupdatecount"), booleanChoices);
        choicesMap.put(Messages.get("prop.namedpipe"), booleanChoices);
        choicesMap.put(Messages.get("prop.useunicode"), booleanChoices);

        final String[] prepareSqlChoices = new String[]{
            String.valueOf(TdsCore.UNPREPARED),
            String.valueOf(TdsCore.TEMPORARY_STORED_PROCEDURES),
            String.valueOf(TdsCore.EXECUTE_SQL),
            String.valueOf(TdsCore.PREPARE),
            String.valueOf(TdsCore.PREPEXEC),
        };
        choicesMap.put(Messages.get("prop.preparesql"), prepareSqlChoices);

        final String[] serverTypeChoices = new String[]{
            String.valueOf(SQLSERVER),
            String.valueOf(SYBASE),
        };
        choicesMap.put(Messages.get("prop.servertype"), serverTypeChoices);

        final String[] tdsChoices = new String[]{
            DefaultProperties.TDS_VERSION_42,
            DefaultProperties.TDS_VERSION_50,
            DefaultProperties.TDS_VERSION_70,
            DefaultProperties.TDS_VERSION_80,
        };
        choicesMap.put(Messages.get("prop.tds"), tdsChoices);

        return choicesMap;
    }

    /**
     * Creates a map of driver properties that should be marked as
     * required when calling {@link #getPropertyInfo(String, Properties)}.
     * <p/>
     * Note that only the key of the map is used to determine whether
     * the <code>required</code> field should be set to <code>true</code>.
     * If the key does not exist in the map, then the <code>required</code>
     * field is set to <code>false</code>.
     * 
     * @return The map of {@link DriverPropertyInfo} objects where
     *         <code>required</code> should be set to <code>true</code>.
     */
    private Map createRequiredTrueMap() {
        final HashMap requiredTrueMap = new HashMap();
        requiredTrueMap.put(Messages.get("prop.servername"), null);
        requiredTrueMap.put(Messages.get("prop.servertype"), null);
        return requiredTrueMap;
    }

    /**
     * Parse the driver URL and extract the properties.
     *
     * @param url The URL to parse.
     * @param info Any existing properties already loaded in a Properties object.
     * @return The URL properties as a <code>Properties</code> object.
     */
    private static Properties parseURL(String url, Properties info) {
        Properties props = new Properties();

        // Take local copy of existing properties
        for (Enumeration e = info.keys(); e.hasMoreElements();) {
            String key = (String) e.nextElement();
            String value = info.getProperty(key);
            
            if (value != null) {
                props.setProperty(key.toUpperCase(), value);
            }
        }

        StringBuffer token = new StringBuffer(16);
        int pos = 0;

        pos = nextToken(url, pos, token); // Skip jdbc

        if (!token.toString().equalsIgnoreCase("jdbc")) {
            return null; // jdbc: missing
        }

        pos = nextToken(url, pos, token); // Skip jtds
        
        if (!token.toString().equalsIgnoreCase("jtds")) {
            return null; // jtds: missing
        }

        pos = nextToken(url, pos, token); // Get server type
        String type = token.toString().toLowerCase();

        Integer serverType = DefaultProperties.getServerType(type);
        if (serverType == null) {
            return null; // Bad server type
        }
        props.setProperty(Messages.get("prop.servertype"), String.valueOf(serverType));

        pos = nextToken(url, pos, token); // Null token between : and //

        if (token.length() > 0) {
            return null; // There should not be one!
        }

        pos = nextToken(url, pos, token); // Get server name
        String host = token.toString();

        if (host.length() == 0 &&
            props.getProperty(Messages.get("prop.servername")) == null) {
            return null; // Server name missing
        }

        props.setProperty(Messages.get("prop.servername"), host);

        if (url.charAt(pos - 1) == ':' && pos < url.length()) {
            pos = nextToken(url, pos, token); // Get port number

            try {
                int port = Integer.parseInt(token.toString());
                props.setProperty(Messages.get("prop.portnumber"), Integer.toString(port));
            } catch(NumberFormatException e) {
                return null; // Bad port number
            }
        }

        if (url.charAt(pos - 1) == '/' && pos < url.length()) {
            pos = nextToken(url, pos, token); // Get database name
            props.setProperty(Messages.get("prop.databasename"), token.toString());
        }

        //
        // Process any additional properties in URL
        //
        while (url.charAt(pos - 1) == ';' && pos < url.length()) {
            pos = nextToken(url, pos, token);
            String tmp = token.toString();
            int index = tmp.indexOf('=');

            if (index > 0 && index < tmp.length() - 1) {
                props.setProperty(tmp.substring(0, index).toUpperCase(), tmp.substring(index + 1));
            } else {
                props.setProperty(tmp.toUpperCase(), "");
            }
        }

        //
        // Set default properties
        //
        props = DefaultProperties.addDefaultProperties(props);

        return props;
    }

    /**
     * Extract the next lexical token from the URL.
     *
     * @param url The URL being parsed
     * @param pos The current position in the URL string.
     * @param token The buffer containing the extracted token.
     * @return The updated position as an <code>int</code>.
     */
    private static int nextToken(String url, int pos, StringBuffer token) {
        token.setLength(0);

        while (pos < url.length()) {
            char ch = url.charAt(pos++);

            if (ch == ':' || ch == ';') {
                break;
            }

            if (ch == '/') {
                if (pos < url.length() && url.charAt(pos) == '/') {
                    pos++;
                }

                break;
            }

            token.append(ch);
        }

        return pos;
    }
}
