package org.dasein.cloud.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * [Class Documentation]
 * <p>Created by George Reese: 2/17/13 7:38 PM</p>
 *
 * @author George Reese
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({ StatefulTestSuite.class, StatelessTestSuite.class })
public class GlobalTestSuite extends AbstractStatefulTestSuite {

}
