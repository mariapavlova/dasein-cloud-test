/**
 * Copyright (C) 2009-2014 Dell, Inc.
 * See annotations for authorship information
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */

package org.dasein.cloud.test.platform;

import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.platform.Database;
import org.dasein.cloud.platform.DatabaseEngine;
import org.dasein.cloud.platform.DatabaseProduct;
import org.dasein.cloud.platform.PlatformServices;
import org.dasein.cloud.platform.RelationalDatabaseSupport;
import org.dasein.cloud.test.DaseinTestManager;
import org.dasein.util.uom.time.Day;
import org.dasein.util.uom.time.TimePeriod;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import javax.annotation.Nonnull;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Tests for validating stateless functionality in support of relational databases in Dasein Cloud.
 * <p>Created by George Reese: 2/27/13 7:15 PM</p>
 * @author George Reese
 * @version 2013.04 initial version
 * @since 2013.04
 */
public class StatelessRDBMSTests {
    static private DaseinTestManager tm;

    @BeforeClass
    static public void configure() {
        tm = new DaseinTestManager(StatelessRDBMSTests.class);
    }

    @AfterClass
    static public void cleanUp() {
        if( tm != null ) {
            tm.close();
        }
    }

    @Rule
    public final TestName name = new TestName();

    private String testDatabaseId;

    public StatelessRDBMSTests() { }

    @Before
    public void before() {
        tm.begin(name.getMethodName());
        assumeTrue(!tm.isTestSkipped());
        testDatabaseId = tm.getTestRDBMSId(DaseinTestManager.STATELESS, false, null);
    }

    @After
    public void after() {
        tm.end();
    }

    @Test
    public void checkMetaData() throws CloudException, InternalException {
        PlatformServices services = tm.getProvider().getPlatformServices();

        if( services == null ) {
            tm.ok("Platform services are not supported in " + tm.getContext().getRegionId() + " of " + tm.getProvider().getCloudName());
            return;
        }
        RelationalDatabaseSupport support = services.getRelationalDatabaseSupport();

        if( support == null ) {
            tm.ok("Relational database support is not implemented for " + tm.getContext().getRegionId() + " in " + tm.getProvider().getCloudName());
            return;
        }
        tm.out("Subscribed", support.isSubscribed());
        tm.out("Term for Database", support.getProviderTermForDatabase(Locale.getDefault()));
        tm.out("Term for Database Snapshot", support.getProviderTermForSnapshot(Locale.getDefault()));
        tm.out("Supports Firewall Rules", support.isSupportsFirewallRules());
        tm.out("High Availability Support", support.isSupportsHighAvailability());
        tm.out("Low Availability Support", support.isSupportsLowAvailability());
        tm.out("Maintenance Window Support", support.isSupportsMaintenanceWindows());
        tm.out("Supports Snapshots", support.isSupportsSnapshots());

        Iterable<DatabaseEngine> engines = support.getDatabaseEngines();

        if( engines != null ) {
            for( DatabaseEngine engine : engines ) {
                tm.out("Default Version [" + engine + "]", support.getDefaultVersion(engine));
            }
            for( DatabaseEngine engine : engines ) {
                tm.out("Supported Versions [" + engine + "]", support.getSupportedVersions(engine));
            }
        }
        assertNotNull("The provider term for a database may not be null", support.getProviderTermForDatabase(Locale.getDefault()));
        assertNotNull("The provider term for a database snapshot may not be null", support.getProviderTermForSnapshot(Locale.getDefault()));
        for( DatabaseEngine engine : support.getDatabaseEngines() ) {
            Iterable<DatabaseProduct> products = support.getDatabaseProducts(engine);
            Iterable<String> versions = support.getSupportedVersions(engine);

            assertNotNull("The list of database products for " + engine + " may not be null, even if not supported", products);
            assertNotNull("The list of supported database versions for " + engine + " may not be null, even if not supported", versions);
            if( support.isSubscribed() && engines != null ) {
                for( DatabaseEngine supported : engines ) {
                    if( supported.equals(engine) ) {
                        assertTrue("There must be at least one supported version for every supported database engine (" + engine +  " missing)", versions.iterator().hasNext());
                    }
                }
            }
        }
        if( engines != null ) {
            for( DatabaseEngine engine : engines ) {
                assertNotNull("The default version for a supported database engine (" + engine + ") cannot be null", support.getDefaultVersion(engine));
            }
        }
    }

    @Test
    public void listDatabaseEngines() throws CloudException, InternalException {
        PlatformServices services = tm.getProvider().getPlatformServices();

        if( services == null ) {
            tm.ok("Platform services are not supported in " + tm.getContext().getRegionId() + " of " + tm.getProvider().getCloudName());
            return;
        }
        RelationalDatabaseSupport support = services.getRelationalDatabaseSupport();

        if( support == null ) {
            tm.ok("Relational database support is not implemented for " + tm.getContext().getRegionId() + " in " + tm.getProvider().getCloudName());
            return;
        }
        Iterable<DatabaseEngine> engines = support.getDatabaseEngines();
        int count = 0;

        assertNotNull("The list of database engines may not be null", engines);
        for( DatabaseEngine engine : engines ) {
            count++;
            tm.out("RDBMS Engine", engine);
        }
        tm.out("Total Database Engine Count", count);
        if( count < 1 ) {
            if( !support.isSubscribed() ) {
                tm.ok("This account is not subscribed to RDBMS support in " + tm.getContext().getRegionId() + " of " + tm.getProvider().getCloudName());
            }
            else {
                fail("There must be at least one supported database engine");
            }
        }
    }

    @Test
    public void listDatabaseProducts() throws CloudException, InternalException {
        PlatformServices services = tm.getProvider().getPlatformServices();

        if( services == null ) {
            tm.ok("Platform services are not supported in " + tm.getContext().getRegionId() + " of " + tm.getProvider().getCloudName());
            return;
        }
        RelationalDatabaseSupport support = services.getRelationalDatabaseSupport();

        if( support == null ) {
            tm.ok("Relational database support is not implemented for " + tm.getContext().getRegionId() + " in " + tm.getProvider().getCloudName());
            return;
        }
        Iterable<DatabaseEngine> engines = support.getDatabaseEngines();

        for( DatabaseEngine engine : DatabaseEngine.values() ) {
            Iterable<DatabaseProduct> products = support.getDatabaseProducts(engine);
            int count = 0;

            assertNotNull("The list of database products may not be null, even if the engine is not supported", products);
            for( DatabaseProduct product : products ) {
                count++;
                tm.out("RDBMS Product [" + engine + "]", product);
            }
            tm.out("Total " + engine + " Database Product Count", count);
            boolean supported = false;

            for( DatabaseEngine dbe : engines ) {
                if( dbe.equals(engine) ) {
                    supported = true;
                    break;
                }
            }
            if( count < 1 ) {
                if( !support.isSubscribed() ) {
                    tm.ok("This account is not subscribed to RDBMS support in " + tm.getContext().getRegionId() + " of " + tm.getProvider().getCloudName());
                }
                else if( supported ) {
                    fail("There must be at least one product for each supported database engine (missing one for " + engine + ")");
                }
            }
        }
    }

    @Test
    public void getBogusDatabase() throws CloudException, InternalException {
        PlatformServices services = tm.getProvider().getPlatformServices();

        if( services == null ) {
            tm.ok("Platform services are not supported in " + tm.getContext().getRegionId() + " of " + tm.getProvider().getCloudName());
            return;
        }
        RelationalDatabaseSupport support = services.getRelationalDatabaseSupport();

        if( support == null ) {
            tm.ok("Relational database support is not implemented for " + tm.getContext().getRegionId() + " in " + tm.getProvider().getCloudName());
            return;
        }
        Database database = support.getDatabase(UUID.randomUUID().toString());

        tm.out("Bogus Database", database);
        assertNull("The random UUID resulted in a database being returned, should be null", database);
    }

    @Test
    public void getDatabase() throws CloudException, InternalException {
        PlatformServices services = tm.getProvider().getPlatformServices();

        if( services == null ) {
            tm.ok("Platform services are not supported in " + tm.getContext().getRegionId() + " of " + tm.getProvider().getCloudName());
            return;
        }
        RelationalDatabaseSupport support = services.getRelationalDatabaseSupport();

        if( support == null ) {
            tm.ok("Relational database support is not implemented for " + tm.getContext().getRegionId() + " in " + tm.getProvider().getCloudName());
            return;
        }
        if( testDatabaseId != null ) {
            Database database = support.getDatabase(testDatabaseId);

            tm.out("Database", database);
            assertNotNull("The test database returned null", database);
        }
        else if( !support.isSubscribed() ) {
            tm.ok("This account is not subscribed to relational database support in " + tm.getContext().getRegionId() + " in " + tm.getProvider().getCloudName());
        }
        else {
            fail("No test database has been identified for this test");
        }
    }

    private void assertDatabase(@Nonnull Database db) {
        assertNotNull("Database ID is null", db.getProviderDatabaseId());
        assertNotNull("Status is null", db.getCurrentState());
        assertNotNull("Name is null", db.getName());
        assertNotNull("Product is null", db.getProductSize());
        assertNotNull("Region is null", db.getProviderRegionId());
        assertNotNull("Engine is null", db.getEngine());
        assertEquals("Region must match the current region", tm.getContext().getRegionId(), db.getProviderRegionId());
    }

    @Test
    public void databaseContent() throws CloudException, InternalException {
        PlatformServices services = tm.getProvider().getPlatformServices();

        if( services == null ) {
            tm.ok("Platform services are not supported in " + tm.getContext().getRegionId() + " of " + tm.getProvider().getCloudName());
            return;
        }
        RelationalDatabaseSupport support = services.getRelationalDatabaseSupport();

        if( support == null ) {
            tm.ok("Relational database support is not implemented for " + tm.getContext().getRegionId() + " in " + tm.getProvider().getCloudName());
            return;
        }
        if( testDatabaseId != null ) {
            Database db = support.getDatabase(testDatabaseId);

            assertNotNull("The test database returned null", db);

            tm.out("RDBMS ID", db.getProviderDatabaseId());
            tm.out("Current State", db.getCurrentState());
            tm.out("Name", db.getName());
            tm.out("Created", (new Date(db.getCreationTimestamp())));
            tm.out("Owner Account", db.getProviderOwnerId());
            tm.out("Region ID", db.getProviderRegionId());
            tm.out("Data Center ID", db.getProviderDataCenterId());
            tm.out("Product", db.getProductSize());
            tm.out("Engine", db.getEngine());
            tm.out("High Availability", db.isHighAvailability());
            tm.out("Location", db.getHostName() + ":" + db.getHostPort());
            tm.out("Storage", db.getAllocatedStorageInGb() + " GB");
            tm.out("Recovery Point", (new Date(db.getRecoveryPointTimestamp())));
            tm.out("Snapshot Window", db.getSnapshotWindow());
            tm.out("Snapshot Retention", new TimePeriod<Day>(db.getSnapshotRetentionInDays(), TimePeriod.DAY));
            tm.out("Maintenance Window", db.getMaintenanceWindow());
            tm.out("Admin User", db.getAdminUser());
            tm.out("Configuration", db.getConfiguration());

            assertDatabase(db);
        }
        else if( !support.isSubscribed() ) {
            tm.ok("This account is not subscribed to relational database support in " + tm.getContext().getRegionId() + " in " + tm.getProvider().getCloudName());
        }
        else {
            fail("No test database has been identified for this test");
        }
    }

    @Test
    public void listDatabases() throws CloudException, InternalException {
        PlatformServices services = tm.getProvider().getPlatformServices();

        if( services == null ) {
            tm.ok("Platform services are not supported in " + tm.getContext().getRegionId() + " of " + tm.getProvider().getCloudName());
            return;
        }
        RelationalDatabaseSupport support = services.getRelationalDatabaseSupport();

        if( support == null ) {
            tm.ok("Relational database support is not implemented for " + tm.getContext().getRegionId() + " in " + tm.getProvider().getCloudName());
            return;
        }
        Iterable<Database> databases = support.listDatabases();
        int count = 0;

        assertNotNull("The list of databases may not be null, even if not subscribed", databases);
        for( Database db : databases ) {
            count++;
            tm.out("Database", db);
        }
        tm.out("Total Database Count", count);
        if( !support.isSubscribed() ) {
            assertEquals("The database count must be zero since the account is not subscribed", 0, count);
        }
        else if( count < 1 ) {
            tm.warn("This test is likely invalid as no databases were provided in the results for validation");
        }
        for( Database db : databases ) {
            assertDatabase(db);
        }
    }

    @Test
    public void listDatabaseStatus() throws CloudException, InternalException {
        PlatformServices services = tm.getProvider().getPlatformServices();

        if( services == null ) {
            tm.ok("Platform services are not supported in " + tm.getContext().getRegionId() + " of " + tm.getProvider().getCloudName());
            return;
        }
        RelationalDatabaseSupport support = services.getRelationalDatabaseSupport();

        if( support == null ) {
            tm.ok("Relational database support is not implemented for " + tm.getContext().getRegionId() + " in " + tm.getProvider().getCloudName());
            return;
        }
        Iterable<ResourceStatus> databases = support.listDatabaseStatus();
        int count = 0;

        assertNotNull("The list of databases may not be null, even if not subscribed", databases);
        for( ResourceStatus db : databases ) {
            count++;
            tm.out("Database Status", db);
        }
        tm.out("Total Database Status Count", count);
        if( !support.isSubscribed() ) {
            assertEquals("The database status count must be zero since the account is not subscribed", 0, count);
        }
        else if( count < 1 ) {
            tm.warn("This test is likely invalid as no database status items were provided in the results for validation");
        }
    }

    @Test
    public void compareDatabaseListAndStatus() throws CloudException, InternalException {
        PlatformServices services = tm.getProvider().getPlatformServices();

        if( services == null ) {
            tm.ok("Platform services are not supported in " + tm.getContext().getRegionId() + " of " + tm.getProvider().getCloudName());
            return;
        }
        RelationalDatabaseSupport support = services.getRelationalDatabaseSupport();

        if( support == null ) {
            tm.ok("Relational database support is not implemented for " + tm.getContext().getRegionId() + " in " + tm.getProvider().getCloudName());
            return;
        }
        HashMap<String,Map<String,Boolean>> map = new HashMap<String, Map<String, Boolean>>();
        Iterable<Database> databases = support.listDatabases();
        Iterable<ResourceStatus> status = support.listDatabaseStatus();

        assertNotNull("listDatabases() must return at least an empty collections and may not be null", databases);
        assertNotNull("listDatabaseStatus() must return at least an empty collection and may not be null", status);
        for( ResourceStatus s : status ) {
            Map<String,Boolean> current = map.get(s.getProviderResourceId());

            if( current == null ) {
                current = new HashMap<String, Boolean>();
                map.put(s.getProviderResourceId(), current);
            }
            current.put("status", true);
        }
        for( Database db : databases ) {
            Map<String,Boolean> current = map.get(db.getProviderDatabaseId());

            if( current == null ) {
                current = new HashMap<String, Boolean>();
                map.put(db.getProviderDatabaseId(), current);
            }
            current.put("database", true);
        }
        for( Map.Entry<String,Map<String,Boolean>> entry : map.entrySet() ) {
            Boolean s = entry.getValue().get("status");
            Boolean d = entry.getValue().get("database");

            assertTrue("Status and database lists do not match for " + entry.getKey(), s != null && d != null && s && d);
        }
        tm.out("Matches");
    }
}
