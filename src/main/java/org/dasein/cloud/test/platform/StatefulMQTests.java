package org.dasein.cloud.test.platform;

import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.test.DaseinTestManager;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import static org.junit.Assume.assumeTrue;

/**
 * Implements integration testing for stateful operations against cloud message queue services.
 * <p>Created by George Reese: 7/24/13 10:32 AM</p>
 * @author George Reese
 * @version 2013.07 initial version (issue #6)
 * @since 2013.07
 */
public class StatefulMQTests {
    static private DaseinTestManager tm;

    @BeforeClass
    static public void configure() {
        tm = new DaseinTestManager(StatefulMQTests.class);
    }

    @AfterClass
    static public void cleanUp() {
        if( tm != null ) {
            tm.close();
        }
    }

    @Rule
    public final TestName name = new TestName();

    private String testQueueId;

    public StatefulMQTests() { }

    @Before
    public void before() {
        tm.begin(name.getMethodName());
        assumeTrue(!tm.isTestSkipped());
        if( name.getMethodName().equalsIgnoreCase("removeMessageQueue") ) {
            testQueueId = tm.getTestQueueId(DaseinTestManager.REMOVED, true);
        }
        else if( !name.getMethodName().equalsIgnoreCase("createMessageQueue") ) {
            testQueueId = tm.getTestQueueId(DaseinTestManager.STATEFUL, true);
        }
    }

    @After
    public void after() {
        try {
            testQueueId = null;
        }
        finally {
            tm.end();
        }
    }

    @Test
    public void createMessageQueue() throws CloudException, InternalException {
        // TODO: implement me
    }

    @Test
    public void removeMessageQueue() throws CloudException, InternalException {
        // TODO: implement me
    }

    @Test
    public void sendMessage() throws CloudException, InternalException {
        // TODO: implement me
    }

    @Test
    public void receiveMessage() throws CloudException, InternalException {
        // TODO: implement me
    }

    @Test
    public void receiveMessages() throws CloudException, InternalException {
        // TODO: implement me
    }
}