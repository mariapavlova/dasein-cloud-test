/**
 * Copyright (C) 2009-2015 Dell, Inc.
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

package org.dasein.cloud.test.compute;

import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.dc.DataCenterServices;
import org.dasein.cloud.dc.Folder;
import org.dasein.cloud.dc.FolderType;
import org.dasein.cloud.dc.StoragePool;
import org.dasein.cloud.network.*;
import org.dasein.cloud.test.DaseinTestManager;
import org.dasein.cloud.test.network.NetworkResources;
import org.dasein.util.CalendarWrapper;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Storage;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * [Class Documentation]
 * <p>Created by George Reese: 2/17/13 7:46 PM</p>
 *
 * @author George Reese
 */
public class StatefulVMTests {
    static private DaseinTestManager tm;
    @Rule
    public final TestName name = new TestName();

    private String testVmId = null;

    @BeforeClass
    static public void configure() {
        tm = new DaseinTestManager(StatefulVMTests.class);
    }

    @AfterClass
    static public void cleanUp() {
        if( tm != null ) {
            tm.close();
        }
    }

    private String testDataCenterId;
    private String testResourcePoolId;

    public StatefulVMTests() {
    }

    private @Nullable VirtualMachine awaitState( @Nonnull VirtualMachine vm, @Nonnull VmState targetState, @Nonnegative long timeout ) {
        VmState currentState = vm.getCurrentState();
        VirtualMachine v = vm;
        int gone = 0;

        while( System.currentTimeMillis() < timeout ) {
            if( targetState.equals(currentState) ) {
                return v;
            }
            try {
                Thread.sleep(60000L);
            } catch( InterruptedException ignore ) {
            }
            try {
                //noinspection ConstantConditions
                v = tm.getProvider().getComputeServices().getVirtualMachineSupport().getVirtualMachine(vm.getProviderVirtualMachineId());
                if( v == null && !targetState.equals(VmState.TERMINATED) ) {
                    gone++;
                    if( gone > 10 ) {
                        return null;
                    }
                }
                else if( v == null ) {
                    return null;
                }
                else {
                    currentState = v.getCurrentState();
                }
            } catch( Throwable ignore ) {
                // ignore
            }
        }
        return v;
    }

    @Before
    public void before() {
        tm.begin(name.getMethodName());
        assumeTrue(!tm.isTestSkipped());

        testDataCenterId = tm.getTestDataCenterId(false);

        ComputeServices services = tm.getProvider().getComputeServices();
        if( services == null || services.getVirtualMachineSupport() == null ) {
            return;
        }
        VirtualMachineSupport support = services.getVirtualMachineSupport();

        if( name.getMethodName().equals("filterVMs") ) {
            try {
                //noinspection ConstantConditions
                testVmId = DaseinTestManager.getComputeResources().provisionVM(support, "filter", "dasein-filter-test", "dsnfilter", testDataCenterId);

            } catch( Throwable t ) {
                tm.warn("Failed to provision VM for filter test: " + t.getMessage());
            }
        }
        else if( name.getMethodName().equals("reboot") ) {
            testVmId = tm.getTestVMId(DaseinTestManager.STATEFUL, VmState.RUNNING, true, testDataCenterId);
        }
        else if( name.getMethodName().equals("terminate") ) {
            testVmId = tm.getTestVMId(DaseinTestManager.REMOVED, VmState.RUNNING, true, testDataCenterId);
        }
        else if( name.getMethodName().equals("start") ) {
            testVmId = tm.getTestVMId(DaseinTestManager.STATEFUL, VmState.STOPPED, true, testDataCenterId);
        }
        else if( name.getMethodName().equals("stop") ) {
            testVmId = tm.getTestVMId(DaseinTestManager.STATEFUL, VmState.RUNNING, true, testDataCenterId);
        }
        else if( name.getMethodName().startsWith("modifyInstance") ) {
            VmState vmStateForAlter = null;
            for( VmState state : VmState.values() ) {
                try {
                    if( support.getCapabilities().canAlter(state) ) {
                        vmStateForAlter = state;
                        break;
                    }
                }
                catch( CloudException e ) {
                }
                catch( InternalException e ) {
                }
            }
            if( vmStateForAlter == null ) {
                tm.warn("Failed to find a supported VM state for modifyInstance state.");
                return;
            }
            testVmId = tm.getTestVMId(DaseinTestManager.STATEFUL, vmStateForAlter, true, testDataCenterId);
        }
        else if( name.getMethodName().equals("pause") ) {
            testVmId = tm.getTestVMId(DaseinTestManager.STATEFUL, VmState.RUNNING, true, testDataCenterId);
        }
        else if( name.getMethodName().equals("unpause") ) {
            testVmId = tm.getTestVMId(DaseinTestManager.STATEFUL, VmState.PAUSED, true, testDataCenterId);
        }
        else if( name.getMethodName().equals("suspend") ) {
            testVmId = tm.getTestVMId(DaseinTestManager.STATEFUL, VmState.RUNNING, true, testDataCenterId);
        }
        else if( name.getMethodName().equals("resume") ) {
            testVmId = tm.getTestVMId(DaseinTestManager.STATEFUL, VmState.SUSPENDED, true, testDataCenterId);
        }
        else if( !name.getMethodName().startsWith("launchVMWith")) {
            testVmId = tm.getTestVMId(DaseinTestManager.STATEFUL, null, true, testDataCenterId);
        }
    }

    @After
    public void after() {
        testVmId = null;
        tm.end();
    }

    @Test
    public void disableAnalytics() throws CloudException, InternalException {
        assumeTrue(!tm.isTestSkipped());
        ComputeServices services = tm.getProvider().getComputeServices();

        if( services != null ) {
            VirtualMachineSupport support = services.getVirtualMachineSupport();

            if( support != null ) {
                if( testVmId != null ) {
                    support.disableAnalytics(testVmId);
                }
                else {
                    tm.warn("No test virtual machine was found for testing enabling analytics");
                }
            }
            else {
                tm.ok("No virtual machine support in this cloud");
            }
        }
        else {
            tm.ok("No compute services in this cloud");
        }
    }

    @Test
    public void enableAnalytics() throws CloudException, InternalException {
        assumeTrue(!tm.isTestSkipped());
        ComputeServices services = tm.getProvider().getComputeServices();

        if( services != null ) {
            VirtualMachineSupport support = services.getVirtualMachineSupport();

            if( support != null ) {
                if( testVmId != null ) {
                    support.enableAnalytics(testVmId);
                }
                else {
                    tm.warn("No test virtual machine was found for testing enabling analytics");
                }
            }
            else {
                tm.ok("No virtual machine support in this cloud");
            }
        }
        else {
            tm.ok("No compute services in this cloud");
        }
    }

    @Test
    public void launch() throws CloudException, InternalException {
        assumeTrue(!tm.isTestSkipped());
        ComputeServices services = tm.getProvider().getComputeServices();
        if( services != null ) {
            VirtualMachineSupport support = services.getVirtualMachineSupport();

            if( support != null ) {
                if( support.isSubscribed() ) {
                    @SuppressWarnings("ConstantConditions") String id = DaseinTestManager.getComputeResources().provisionVM(support, "testLaunch", "dasein-test-launch-" + tm.getUserName(), "dsnlaunch", testDataCenterId);

                    tm.out("Launched", id);
                    assertNotNull("Attempts to provisionVM a virtual machine MUST return a valid ID", id);
                    assertNotNull("Could not find the newly created virtual machine", support.getVirtualMachine(id));
                    if( !Requirement.NONE.equals(support.getCapabilities().identifyShellKeyRequirement(DaseinTestManager.getComputeResources().getTestImagePlatform())) ) {
                        VirtualMachine vm = support.getVirtualMachine(id);
                        if( vm.getProviderKeypairId() == null ) {
                            // not failing, but logging a warning. the key requirement does
                            // not necessarily mean the cloud is able/willing to return the key id
                            tm.warn("Cloud " + tm.getProvider().getCloudName() + " requests to set bootstrap shell key, but is not returning the key pair id for the vm");
                        }
                    }
                }
                else {
                    try {
                        //noinspection ConstantConditions
                        DaseinTestManager.getComputeResources().provisionVM(support, "failure", "Should Fail", "failure", testDataCenterId);
                        fail("Attempt to launch VM should not succeed when the account is not subscribed to virtual machine services");
                    } catch( CloudException ok ) {
                        tm.ok("Got exception when not subscribed: " + ok.getMessage());
                    }
                }
            }
            else {
                tm.ok("No virtual machine support in this cloud");
            }
        }
        else {
            tm.ok("No compute services in this cloud");
        }
    }

    @Test
    public void launchVMWithIP() throws CloudException, InternalException {
        assumeTrue(!tm.isTestSkipped());

        ComputeServices services = tm.getProvider().getComputeServices();
        if( services == null ) {
            tm.ok("No compute services in this cloud");
            return;
        }
        VirtualMachineSupport support = services.getVirtualMachineSupport();

        if( support == null ) {
            tm.ok("No virtual machine support in this cloud");
            return;
        }
        if( !support.isSubscribed() ) {
            try {
                //noinspection ConstantConditions
                DaseinTestManager.getComputeResources().provisionVM(support, "failure", "Should Fail", "failure", null);
                fail("Attempt to launch VM should not succeed when the account is not subscribed to virtual machine services");
            } catch( CloudException ok ) {
                tm.ok("Got exception when not subscribed: " + ok.getMessage());
                return;
            }
        }

        ComputeResources compute = DaseinTestManager.getComputeResources();
        if( compute == null ) {
            fail("Test compute resources aren't initialised. Panic!");
        }
        String imageId = tm.getTestImageId(DaseinTestManager.STATELESS, false);

        assertNotNull("Unable to identify a test image for test launch", imageId);
        String productId = tm.getTestVMProductId();

        VMLaunchOptions options = VMLaunchOptions.getInstance(productId, imageId, "dsnVmIp" + ( System.currentTimeMillis() % 10000 ), "Dasein Vm With IP Launch " + System.currentTimeMillis(), "Test launch for a VM with an IP");

//        String testVmIp = "192.168.200.150";
        String testVmIp = "192.168.101.100";
        options.withPrivateIp(testVmIp);
        options.withGatewayList("192.168.101.1");
        options.withDnsServerList("192.168.101.1");
        options.withMetaData("vSphereNetMaskNothingToSeeHere", "255.255.255.0");
//        options.withDnsDomain("dasein.org");
        String vlanId = tm.getTestVLANId(DaseinTestManager.STATELESS, true, options.getDataCenterId());
        if( vlanId != null ) {
            options.inVlan(null, testDataCenterId, vlanId);
            NetworkServices networkServices = tm.getProvider().getNetworkServices();
            if( networkServices == null ) {
                fail("Test is inconsitent: vlanId is specified, but the cloud doesn't have network services");
            }
            VLANSupport vlanSupport = networkServices.getVlanSupport();
            if( vlanSupport == null ) {
                fail("Test is inconsitent: vlanId is specified, but the cloud doesn't have VLAN support");
            }
            VLAN vlan = vlanSupport.getVlan(vlanId);
            if( vlan == null ) {
                fail("Test is inconsitent: vlanId is specified, but VLAN is not found");
            }
            Iterable<Subnet> subnets = vlanSupport.listSubnets(vlanId);
            for( Subnet subnet : subnets ) {
                System.out.println(subnet.getName());
            }
//            options.withDnsServerList(vlan.getDnsServers());
//            options.withDnsDomain(vlan.getDomainName());
        }
        String id = compute.provisionVM(support, "VmWithIpLaunch", options, options.getDataCenterId());

        tm.out("Launched", id);
        assertNotNull("Attempts to provisionVM a virtual machine MUST return a valid ID", id);

        VirtualMachine virtualMachine = support.getVirtualMachine(id);
        assertNotNull("Could not find the newly created virtual machine", virtualMachine);
        boolean ipFound = false;
        for( RawAddress ra : virtualMachine.getPrivateAddresses() ) {
            if( testVmIp.equals(ra.getIpAddress()) ) {
                ipFound = true;
                break;
            }
        }
        if (support.getCapabilities().isUserDefinedPrivateIPSupported()) {
            assertTrue("Expected IP not found " + testVmIp, ipFound);
        }
        else {
            assertFalse("Private IP should not have been assigned, according to driver capabilities: " + testVmIp, ipFound);

//=======
//        if( services != null ) {
//            VirtualMachineSupport support = services.getVirtualMachineSupport();
//
//            if( support != null ) {
//                if( support.isSubscribed() ) {
//
//                    ComputeResources compute = DaseinTestManager.getComputeResources();
//                    if( compute != null ) {
//                        String imageId = tm.getTestImageId(DaseinTestManager.STATELESS, false);
//
//                        assertNotNull("Unable to identify a test image for test launch", imageId);
//                        String productId = tm.getTestVMProductId();
//
//                        VMLaunchOptions options = VMLaunchOptions.getInstance(productId, imageId, "dsnVmIp" + ( System.currentTimeMillis() % 10000 ), "Dasein Vm With IP Launch " + System.currentTimeMillis(), "Test launch for a VM with an IP");
//
//                        String testVmIp = "192.168.200.200";
//                        options.withPrivateIp(testVmIp);
//
//                        String vlanId = tm.getTestVLANId(DaseinTestManager.STATELESS, true, options.getDataCenterId());
//                        if( vlanId != null ) {
//                            options.inVlan(null, testDataCenterId, vlanId);
//                        }
//                        String id = compute.provisionVM(support, "VmWithIpLaunch", options, options.getDataCenterId());
//
//                        tm.out("Launched", id);
//                        assertNotNull("Attempts to provisionVM a virtual machine MUST return a valid ID", id);
//
//                        VirtualMachine virtualMachine = support.getVirtualMachine(id);
//                        assertNotNull("Could not find the newly created virtual machine", virtualMachine);
//                        boolean ipFound = false;
//                        for( RawAddress ra : virtualMachine.getPrivateAddresses() ) {
//                            if( testVmIp.equals(ra.getIpAddress()) ) {
//                                ipFound = true;
//                                break;
//                            }
//                        }
//                        if (support.getCapabilities().isUserDefinedPrivateIPSupported()) {
//                            assertTrue("Expected IP not found " + testVmIp, ipFound);
//                        }
//                        else {
//                            assertFalse("Private IP should not have been assigned, according to driver capabilities: " + testVmIp, ipFound);
//                        }
//                    }
//                }
//                else {
//                    try {
//                        //noinspection ConstantConditions
//                        DaseinTestManager.getComputeResources().provisionVM(support, "failure", "Should Fail", "failure", null);
//                        fail("Attempt to launch VM should not succeed when the account is not subscribed to virtual machine services");
//                    } catch( CloudException ok ) {
//                        tm.ok("Got exception when not subscribed: " + ok.getMessage());
//                    }
//                }
//            }
//            else {
//                tm.ok("No virtual machine support in this cloud");
//            }
//        }
//        else {
//            tm.ok("No compute services in this cloud");
//>>>>>>> cc2655c730441cf1e8ff17865d268876199d3bbb
        }
    }

    @Test
    public void launchMany() throws CloudException, InternalException {
        assumeTrue(!tm.isTestSkipped());
        ComputeServices services = tm.getProvider().getComputeServices();

        if( services != null ) {
            VirtualMachineSupport support = services.getVirtualMachineSupport();

            if( support != null ) {
                if( support.isSubscribed() ) {
                    @SuppressWarnings("ConstantConditions") Iterable<String> ids = DaseinTestManager.getComputeResources().provisionManyVMs(support, "testLaunch", "dasein-test-launch-" + tm.getUserName(), "dsnlaunch", testDataCenterId, 2);
                    int count = 0;

                    for( String id : ids ) {
                        tm.out("Launched", id);
                        assertNotNull("Attempts to provisionVM a virtual machine MUST return a valid ID", id);
                        assertNotNull("Could not find the newly created virtual machine", support.getVirtualMachine(id));
                        count++;
                    }
                    assertEquals("Two virtual machines were not launched", 2, count);
                }
                else {
                    try {
                        //noinspection ConstantConditions
                        DaseinTestManager.getComputeResources().provisionVM(support, "failure", "Should Fail", "failure", testDataCenterId);
                        fail("Attempt to launch VM should not succeed when the account is not subscribed to virtual machine services");
                    } catch( CloudException ok ) {
                        tm.ok("Got exception when not subscribed: " + ok.getMessage());
                    }
                }
            }
            else {
                tm.ok("No virtual machine support in this cloud");
            }
        }
        else {
            tm.ok("No compute services in this cloud");
        }
    }

    @Test
    public void launchVMWithResourcePool() throws CloudException, InternalException {
        assumeTrue(!tm.isTestSkipped());

        DataCenterServices dcServices = tm.getProvider().getDataCenterServices();
        if (dcServices != null) {
            if (dcServices.getCapabilities().supportsResourcePools()) {
                ComputeServices services = tm.getProvider().getComputeServices();

                if( services != null ) {
                    VirtualMachineSupport support = services.getVirtualMachineSupport();

                    if( support != null ) {
                        if( support.isSubscribed() ) {
                            ComputeResources compute = DaseinTestManager.getComputeResources();
                            String productId = null;
                            if( compute != null ) {
                                boolean foundResourcePool = false;
                                for (Architecture a : support.getCapabilities().listSupportedArchitectures()) {
                                    Iterable<VirtualMachineProduct> products = support.listProducts(a);
                                    for (VirtualMachineProduct p : products) {
                                        //this may not work for clouds other than vsphere so be aware
                                        //currently vsphere is the only cloud with resource pool support
                                        if (p.getProviderProductId().split(":").length==3) {
                                            //found a product that also hold resource pool info
                                            productId = p.getProviderProductId();
                                            foundResourcePool = true;
                                            break;
                                        }
                                    }
                                    if (foundResourcePool) {
                                        break;
                                    }
                                }
                                if (foundResourcePool) {
                                    testResourcePoolId = productId.split(":")[0];
                                }

                                assertNotNull("Unable to identify a VM product for test launch", productId);
                                assertNotNull("Unable to identify a resource pool for test launch", testResourcePoolId);
                                String imageId = tm.getTestImageId(DaseinTestManager.STATELESS, false);

                                assertNotNull("Unable to identify a test image for test launch", imageId);


                                VMLaunchOptions options = VMLaunchOptions.getInstance(productId, imageId, "dsnrespool" + ( System.currentTimeMillis() % 10000 ), "Dasein Resource Pool Launch " + System.currentTimeMillis(), "Test launch for a VM in a resource pool");
                                options.inDataCenter(testDataCenterId);
                                options.withResourcePoolId(testResourcePoolId);
                                String id = compute.provisionVM(support, "resourcePoolLaunch", options, options.getDataCenterId());

                                tm.out("Launched", id);
                                assertNotNull("Attempts to provisionVM a virtual machine MUST return a valid ID", id);

                                VirtualMachine virtualMachine = support.getVirtualMachine(id);
                                tm.out("In resource pool", virtualMachine.getResourcePoolId());
                                assertNotNull("Could not find the newly created virtual machine", virtualMachine);
                                assertEquals("Expected resource pool not found "+testResourcePoolId, testResourcePoolId, virtualMachine.getResourcePoolId());
                            }
                        }
                        else {
                            try {
                                //noinspection ConstantConditions
                                DaseinTestManager.getComputeResources().provisionVM(support, "failure", "Should Fail", "failure", null);
                                fail("Attempt to launch VM should not succeed when the account is not subscribed to virtual machine services");
                            } catch( CloudException ok ) {
                                tm.ok("Got exception when not subscribed: " + ok.getMessage());
                            }
                        }
                    }
                    else {
                        tm.ok("No virtual machine support in this cloud");
                    }
                }
                else {
                    tm.ok("No compute services in this cloud");
                }
            }
            else {
                tm.ok("Resource pools not supported in this cloud");
            }
        }
        else{
            tm.ok("No datacenter services in this cloud");
        }
    }

    @Test
    public void launchVMWithAffinityGroup() throws CloudException, InternalException {
        assumeTrue(!tm.isTestSkipped());

        DataCenterServices dcServices = tm.getProvider().getDataCenterServices();
        if (dcServices != null) {
            if (dcServices.getCapabilities().supportsAffinityGroups()) {
                ComputeServices services = tm.getProvider().getComputeServices();

                if( services != null ) {
                    AffinityGroupSupport affinityGroupSupport = services.getAffinityGroupSupport();
                    AffinityGroupFilterOptions agFilterOptions = AffinityGroupFilterOptions.getInstance().withDataCenterId(testDataCenterId);
                    Iterable<AffinityGroup> affinityGroup = affinityGroupSupport.list(agFilterOptions);
                    if (affinityGroup.iterator().hasNext()) {
                        String testAffinityGroupId = affinityGroup.iterator().next().getAffinityGroupId();

                        VirtualMachineSupport support = services.getVirtualMachineSupport();

                        if( support != null ) {
                            if( support.isSubscribed() ) {
                                ComputeResources compute = DaseinTestManager.getComputeResources();
                                if( compute != null ) {
                                    String productId = tm.getTestVMProductId();

                                    assertNotNull("Unable to identify a VM product for test launch", productId);
                                    String imageId = tm.getTestImageId(DaseinTestManager.STATELESS, false);

                                    assertNotNull("Unable to identify a test image for test launch", imageId);
                                    VMLaunchOptions options = VMLaunchOptions.getInstance(productId, imageId, "dsnAffinity" + ( System.currentTimeMillis() % 10000 ), "Dasein Affinity Group Launch " + System.currentTimeMillis(), "Test launch for a VM in an affinity group");
                                    options.inDataCenter(testDataCenterId);
                                    options.withAffinityGroupId(testAffinityGroupId);
                                    String id = compute.provisionVM(support, "affinityGroupLaunch", options, options.getDataCenterId());

                                    tm.out("Launched", id);
                                    assertNotNull("Attempts to provisionVM a virtual machine MUST return a valid ID", id);

                                    VirtualMachine vm = support.getVirtualMachine(id);
                                    assertNotNull("Could not find the newly created virtual machine", vm);

                                    long timeout = System.currentTimeMillis() + ( CalendarWrapper.MINUTE * 5L );

                                    while( timeout > System.currentTimeMillis() ) {
                                        if( vm == null ) {
                                            break;
                                        }
                                        if( vm.getAffinityGroupId() != null ) {
                                            break;
                                        }
                                        try {
                                            Thread.sleep(15000L);
                                        } catch( InterruptedException ignore ) {
                                        }
                                        try {
                                            vm = support.getVirtualMachine(id);
                                        } catch( Throwable ignore ) {
                                        }
                                    }
                                    tm.out("In affinity group", vm.getAffinityGroupId());
                                    assertNotNull("Launched VM does not exist", vm);
                                    assertEquals("Expected affinity group not found "+testAffinityGroupId, testAffinityGroupId, vm.getAffinityGroupId());
                                }
                            }
                            else {
                                try {
                                    //noinspection ConstantConditions
                                    DaseinTestManager.getComputeResources().provisionVM(support, "failure", "Should Fail", "failure", null);
                                    fail("Attempt to launch VM should not succeed when the account is not subscribed to virtual machine services");
                                } catch( CloudException ok ) {
                                    tm.ok("Got exception when not subscribed: " + ok.getMessage());
                                }
                            }
                        }
                        else {
                            tm.ok("No virtual machine support in this cloud");
                        }
                    }
                    else {
                        fail("No test affinity group found: test invalid");
                    }
                }
                else {
                    tm.ok("No compute services in this cloud");
                }
            }
            else {
                tm.ok("Affinity groups not supported in this cloud");
            }
        }
        else{
            tm.ok("No datacenter services in this cloud");
        }
    }

    @Test
    public void launchVMWithStoragePool() throws CloudException, InternalException {
        assumeTrue(!tm.isTestSkipped());

        DataCenterServices dcServices = tm.getProvider().getDataCenterServices();
        if (dcServices != null) {
            if (dcServices.getCapabilities().supportsStoragePools()) {
                Iterable<StoragePool> pools = dcServices.listStoragePools();
                if (pools.iterator().hasNext()) {
                    String testStoragePoolId = "";
                    for (StoragePool pool : pools) {
                        String dataCenterId = pool.getDataCenterId();
                        if (dataCenterId == null || dataCenterId.equals(testDataCenterId)) {
                            testStoragePoolId = pool.getStoragePoolName();
                            break;
                        }
                    }
                    if (!testStoragePoolId.equals("")) {
                        ComputeServices services = tm.getProvider().getComputeServices();

                        if( services != null ) {
                            VirtualMachineSupport support = services.getVirtualMachineSupport();

                            if( support != null ) {
                                if( support.isSubscribed() ) {
                                    ComputeResources compute = DaseinTestManager.getComputeResources();
                                    if( compute != null ) {
                                        String productId = tm.getTestVMProductId();

                                        assertNotNull("Unable to identify a VM product for test launch", productId);
                                        String imageId = tm.getTestImageId(DaseinTestManager.STATELESS, false);

                                        assertNotNull("Unable to identify a test image for test launch", imageId);
                                        VMLaunchOptions options = VMLaunchOptions.getInstance(productId, imageId, "dsnStorage" + ( System.currentTimeMillis() % 10000 ), "Dasein Storage Pool Launch " + System.currentTimeMillis(), "Test launch for a VM in a storage pool");
                                        options.inDataCenter(testDataCenterId);
                                        options.withStoragePoolId(testStoragePoolId);
                                        String id = compute.provisionVM(support, "storagePoolLaunch", options, options.getDataCenterId());

                                        tm.out("Launched", id);
                                        assertNotNull("Attempts to provisionVM a virtual machine MUST return a valid ID", id);

                                        VirtualMachine vm = support.getVirtualMachine(id);
                                        assertNotNull("Could not find the newly created virtual machine", vm);

                                        long timeout = System.currentTimeMillis() + ( CalendarWrapper.MINUTE * 5L );

                                        while( timeout > System.currentTimeMillis() ) {
                                            if( vm == null ) {
                                                break;
                                            }
                                            Map<String, String> tags = vm.getTags();
                                            if (tags.containsKey("datastore0")) {
                                                break;
                                            }
                                            try {
                                                Thread.sleep(15000L);
                                            } catch( InterruptedException ignore ) {
                                            }
                                            try {
                                                vm = support.getVirtualMachine(id);
                                            } catch( Throwable ignore ) {
                                            }
                                        }

                                        boolean foundStoragePool = false;
                                        Map<String, String> tags = vm.getTags();
                                        for( Map.Entry<String, String> entry : tags.entrySet() ) {
                                            if (entry.getKey().startsWith("datastore")) {
                                                tm.out("In storage pool", entry.getValue());
                                                if (entry.getValue().equals(testStoragePoolId)) {
                                                    foundStoragePool = true;
                                                }
                                            }
                                        }
                                        assertNotNull("Launched VM does not exist", vm);
                                        assertTrue("Expected storage pool not found "+testStoragePoolId, foundStoragePool);
                                    }
                                }
                                else {
                                    try {
                                        //noinspection ConstantConditions
                                        DaseinTestManager.getComputeResources().provisionVM(support, "failure", "Should Fail", "failure", null);
                                        fail("Attempt to launch VM should not succeed when the account is not subscribed to virtual machine services");
                                    } catch( CloudException ok ) {
                                        tm.ok("Got exception when not subscribed: " + ok.getMessage());
                                    }
                                }
                            }
                            else {
                                tm.ok("No virtual machine support in this cloud");
                            }
                        }
                        else {
                            tm.ok("No compute services in this cloud");
                        }
                    }
                    else {
                        fail("Couldn't find valid storage pool for datacenter "+testDataCenterId);
                    }
                }
                else {
                    fail("No test storage pool was found");
                }
            }
            else {
                tm.ok("Storage pools not supported in this cloud");
            }
        }
        else{
            tm.ok("No datacenter services in this cloud");
        }
    }

    @Test
    public void launchVMWithVMFolder() throws CloudException, InternalException {
        assumeTrue(!tm.isTestSkipped());

        DataCenterServices dcServices = tm.getProvider().getDataCenterServices();
        if (dcServices != null) {
            if (dcServices.getCapabilities().supportsFolders()) {
                Iterable<Folder> folders = dcServices.listVMFolders();
                if (folders.iterator().hasNext()) {
                    String testVMFolderId = "";
                    for (Folder folder : folders) {
                        if (FolderType.VM.equals(folder.getType())) {
                            testVMFolderId = folder.getId();
                            break;
                        }
                    }
                    if (!testVMFolderId.equals("")) {
                        ComputeServices services = tm.getProvider().getComputeServices();

                        if( services != null ) {
                            VirtualMachineSupport support = services.getVirtualMachineSupport();

                            if( support != null ) {
                                if( support.isSubscribed() ) {
                                    ComputeResources compute = DaseinTestManager.getComputeResources();
                                    if( compute != null ) {
                                        String productId = tm.getTestVMProductId();

                                        assertNotNull("Unable to identify a VM product for test launch", productId);
                                        String imageId = tm.getTestImageId(DaseinTestManager.STATELESS, false);

                                        assertNotNull("Unable to identify a test image for test launch", imageId);
                                        VMLaunchOptions options = VMLaunchOptions.getInstance(productId, imageId, "dsnFolder" + ( System.currentTimeMillis() % 10000 ), "Dasein VM Folder Launch " + System.currentTimeMillis(), "Test launch for a VM in a folder");
                                        options.inDataCenter(testDataCenterId);
                                        options.withVMFolderId(testVMFolderId);
                                        String id = compute.provisionVM(support, "vmFolderLaunch", options, options.getDataCenterId());

                                        tm.out("Launched", id);
                                        assertNotNull("Attempts to provisionVM a virtual machine MUST return a valid ID", id);

                                        VirtualMachine vm = support.getVirtualMachine(id);
                                        assertNotNull("Could not find the newly created virtual machine", vm);

                                        long timeout = System.currentTimeMillis() + ( CalendarWrapper.MINUTE * 5L );

                                        while( timeout > System.currentTimeMillis() ) {
                                            if( vm == null ) {
                                                break;
                                            }
                                            Map<String, String> tags = vm.getTags();
                                            if (tags.containsKey("vmFolder")) {
                                                break;
                                            }
                                            try {
                                                Thread.sleep(15000L);
                                            } catch( InterruptedException ignore ) {
                                            }
                                            try {
                                                vm = support.getVirtualMachine(id);
                                            } catch( Throwable ignore ) {
                                            }
                                        }

                                        boolean foundFolder = false;
                                        Map<String, String> tags = vm.getTags();
                                        for( Map.Entry<String, String> entry : tags.entrySet() ) {
                                            if (entry.getKey().equals("vmFolder")) {
                                                tm.out("In folder", entry.getValue());
                                                if (entry.getValue().equals(testVMFolderId)) {
                                                    foundFolder = true;
                                                }
                                            }
                                        }
                                        assertNotNull("Launched VM does not exist", vm);
                                        assertTrue("Expected folder not found "+testVMFolderId, foundFolder);
                                    }
                                }
                                else {
                                    try {
                                        //noinspection ConstantConditions
                                        DaseinTestManager.getComputeResources().provisionVM(support, "failure", "Should Fail", "failure", null);
                                        fail("Attempt to launch VM should not succeed when the account is not subscribed to virtual machine services");
                                    } catch( CloudException ok ) {
                                        tm.ok("Got exception when not subscribed: " + ok.getMessage());
                                    }
                                }
                            }
                            else {
                                tm.ok("No virtual machine support in this cloud");
                            }
                        }
                        else {
                            tm.ok("No compute services in this cloud");
                        }
                    }
                    else {
                        fail("Couldn't find valid folder");
                    }
                }
                else {
                    fail("No test folder was found");
                }
            }
            else {
                tm.ok("Folders not supported in this cloud");
            }
        }
        else{
            tm.ok("No datacenter services in this cloud");
        }
    }

    @Test
    public void launchVMWithClientRequestToken() throws CloudException, InternalException {
        assumeTrue(!tm.isTestSkipped());

        ComputeServices services = tm.getProvider().getComputeServices();

        if( services == null ) {
            tm.ok("No compute services in this cloud");
            return;
        }
        VirtualMachineSupport support = services.getVirtualMachineSupport();
        if( support == null ) {
            tm.ok("No virtual machine support in this cloud");
            return;
        }
        if( !support.isSubscribed() ) {
            tm.ok("No virtual machine support subscribed for");
            return;
        }
        if( !support.getCapabilities().supportsClientRequestToken() ) {
            tm.ok("No client request token support in this cloud");
            return;
        }

        ComputeResources compute = DaseinTestManager.getComputeResources();
        if( compute != null ) {
            String productId = tm.getTestVMProductId();
            assertNotNull("Unable to identify a VM product for test launch", productId);
            String imageId = tm.getTestImageId(DaseinTestManager.STATELESS, false);
            assertNotNull("Unable to identify a test image for test launch", imageId);
            String clientToken = "dsntokn" + ( System.currentTimeMillis() % 10000 );
            VMLaunchOptions options = VMLaunchOptions.getInstance(productId, imageId, clientToken, "Dasein Client Token Launch " + System.currentTimeMillis(), "Test launch for a VM with a client token");
            options.withClientRequestToken(clientToken);
            String id = compute.provisionVM(support, "clientRequestTokenLaunch", options, options.getDataCenterId());

            tm.out("Launched", id);
            assertNotNull("Attempts to provisionVM a virtual machine MUST return a valid ID", id);

            VirtualMachine virtualMachine = support.getVirtualMachine(id);
            tm.out("With client request token", virtualMachine.getClientRequestToken());
            assertNotNull("Could not find the newly created virtual machine", virtualMachine);
            assertEquals("Expected client request token not found", clientToken, virtualMachine.getClientRequestToken());
        }
    }

    @Test
    public void filterVMs() throws CloudException, InternalException {
        assumeTrue(!tm.isTestSkipped());
        ComputeServices services = tm.getProvider().getComputeServices();

        if( services != null ) {
            VirtualMachineSupport support = services.getVirtualMachineSupport();

            if( support != null ) {
                Iterable<VirtualMachine> vms = support.listVirtualMachines(VMFilterOptions.getInstance(".*[Ff][Ii][Ll][Tt][Ee][Rr].*"));
                boolean found = false;
                int count = 0;

                assertNotNull("Filtering must return at least an empty collections and may not be null", vms);
                for( VirtualMachine vm : vms ) {
                    count++;
                    if( vm.getProviderVirtualMachineId().equals(testVmId) ) {
                        found = true;
                    }
                    tm.out("VM", vm);
                }
                tm.out("Total VM Count", count);
                if( count < 1 && support.isSubscribed() ) {
                    if( testVmId == null ) {
                        tm.warn("No virtual machines were listed and thus the test may be in error");
                    }
                    else {
                        fail("Should have found test virtual machine " + testVmId + ", but none were found");
                    }
                }
                if( testVmId != null ) {
                    assertTrue("Did not find the test filter VM " + testVmId + " among the filtered VMs", found);
                }
                else {
                    tm.warn("No test VM existed for filter test, so results may not be valid");
                }
            }
            else {
                tm.ok("No virtual machine support in this cloud");
            }
        }
        else {
            tm.ok("No compute services in this cloud");
        }
    }

    @Test
    public void stop() throws CloudException, InternalException {
        assumeTrue(!tm.isTestSkipped());
        ComputeServices services = tm.getProvider().getComputeServices();

        if( services != null ) {
            VirtualMachineSupport support = services.getVirtualMachineSupport();

            if( support != null ) {
                if( testVmId != null ) {
                    VirtualMachine vm = support.getVirtualMachine(testVmId);

                    if( vm != null ) {
                        if( support.getCapabilities().canStop(vm.getCurrentState()) ) {
                            tm.out("Before", vm.getCurrentState());
                            support.stop(testVmId, false);
                            vm = awaitState(vm, VmState.STOPPED, System.currentTimeMillis() + ( CalendarWrapper.MINUTE * 10L ));
                            VmState currentState = ( vm == null ? VmState.TERMINATED : vm.getCurrentState() );
                            tm.out("After", currentState);
                            if( !VmState.STOPPED.equals(currentState) ) {
                                tm.out("VM hasn't stopped, forcing");
                                support.stop(testVmId, true);
                                vm = awaitState(vm, VmState.STOPPED, System.currentTimeMillis() + ( CalendarWrapper.MINUTE * 10L ));
                                currentState = ( vm == null ? VmState.TERMINATED : vm.getCurrentState() );
                                tm.out("After force stop", currentState);
                            }
                            assertEquals("Current state does not match the target state", VmState.STOPPED, currentState);
                        }
                        else {
                            try {
                                support.stop(testVmId);
                                fail("Start/stop is unsupported, yet the method completed without an error");
                            } catch( OperationNotSupportedException expected ) {
                                tm.ok("STOP -> Operation not supported exception");
                            }
                        }
                    }
                    else {
                        tm.warn("Test virtual machine " + testVmId + " no longer exists");
                    }
                }
                else {
                    tm.warn("No test virtual machine was found for this test");
                }
            }
            else {
                tm.ok("No virtual machine support in this cloud");
            }
        }
        else {
            tm.ok("No compute services in this cloud");
        }
    }

    @Test
    public void modifyInstance() throws CloudException, InternalException {
        assumeTrue(!tm.isTestSkipped());
        ComputeServices services = tm.getProvider().getComputeServices();

        if( services == null ) {
            tm.ok("No compute services in this cloud");
            return;
        }

        VirtualMachineSupport support = services.getVirtualMachineSupport();
        if( support == null ) {
            tm.ok("No virtual machine support in this cloud");
            return;
        }

        if( testVmId == null ) {
            tm.warn("No test virtual machine was found for this test");
            return;
        }

        VirtualMachine vm = support.getVirtualMachine(testVmId);
        if( vm == null ) {
            tm.warn("Test virtual machine " + testVmId + " no longer exists");
        }

        if( !support.getCapabilities().canAlter(vm.getCurrentState()) ) {
            tm.ok("Alter vm not supported for vm state " + vm.getCurrentState());
            return;
        }

        VirtualMachineProduct currentProduct = support.getProduct(vm.getProductId());
        assertNotNull("Was unable to get a current product from the vm", currentProduct);

        tm.out("Before", vm.getProductId());
        VirtualMachineProduct newProduct = null;
        for( VirtualMachineProduct product : support.listProducts(vm.getProviderMachineImageId()) ) {
            if( product.getProviderProductId().equals(currentProduct.getProviderProductId()) ) {
                continue;
            }
            if( (product.getCpuCount() > currentProduct.getCpuCount()
                    || product.getRamSize().intValue() > currentProduct.getRamSize().intValue())
                    && product.getRootVolumeSize().longValue() > currentProduct.getRootVolumeSize().longValue() ) {
                newProduct = product;
                break;
            }
        }
        assertNotNull("Was unable to select a new product as alterVm target", newProduct);

        if (support.getCapabilities().getVerticalScalingCapabilities().isSupportsProductSizeChanges()) {
            // TODO(stas): Having to pass strings here is not nice, we'll need to fix it in the next release
            vm = support.alterVirtualMachineSize(testVmId, String.valueOf(newProduct.getCpuCount()), String.valueOf(newProduct.getRamSize().intValue()));
        }
        else if (support.getCapabilities().getVerticalScalingCapabilities().isSupportsProductChanges()) {
            vm = support.alterVirtualMachineProduct(testVmId, newProduct.getProviderProductId());
        }
        else {
            tm.warn("Unable to determine how vm product scaling is handled in "+tm.getProvider().getCloudName());
            tm.warn("Test not attempted");
            return;
        }
//        VMScalingCapabilities scalingCapabilities = support.getCapabilities().getVerticalScalingCapabilities();
//        if (scalingCapabilities.getAlterVmForNewVolume().equals(Requirement.REQUIRED)) {
//            VolumeCreateOptions[] volumes = new VolumeCreateOptions[1];
//            Storage<Gigabyte> size = new Storage<Gigabyte>(5,Storage.GIGABYTE);
//            String description = "testVolumeAdd";
//            VolumeCreateOptions vol = VolumeCreateOptions.getInstance(size,"dsnVolAdd",description);
//            volumes[0] = vol;
//            VMScalingOptions options = VMScalingOptions.getInstance(modifiedProductId).withVolumes(volumes);
//            support.alterVirtualMachine(testVmId, options);
//        }
//        else {
//            support.alterVirtualMachine(testVmId, VMScalingOptions.getInstance(modifiedProductId));
//        }
//        try {
//            Thread.sleep(5000L);
//        } catch (InterruptedException ignore) {
//        }
        if( vm != null ) {
            tm.out("After", vm.getProductId());
            // TODO(stas): I'm not sure this will work in case of the arbitrary memory/cpu changes
            assertEquals("Current product id does not match the target product id", newProduct.getProviderProductId(), vm.getProductId());
        }
    }

    @Test
    public void modifyInstanceWithSmallerProduct() throws CloudException, InternalException {
        assumeTrue(!tm.isTestSkipped());
        ComputeServices services = tm.getProvider().getComputeServices();

        if( services == null ) {
            tm.ok("No compute services in this cloud");
            return;
        }

        VirtualMachineSupport support = services.getVirtualMachineSupport();
        if( support == null ) {
            tm.ok("No virtual machine support in this cloud");
            return;
        }

        if( testVmId == null ) {
            tm.warn("No test virtual machine was found for this test");
            return;
        }

        VirtualMachine vm = support.getVirtualMachine(testVmId);
        if( vm == null ) {
            tm.warn("Test virtual machine " + testVmId + " no longer exists");
        }

        if( !support.getCapabilities().canAlter(vm.getCurrentState()) ) {
            tm.ok("Alter vm not supported for vm state " + vm.getCurrentState());
            return;
        }

        VirtualMachineProduct currentProduct = support.getProduct(vm.getProductId());
        assertNotNull("Was unable to get a current product from the vm", currentProduct);

        tm.out("Before", vm.getProductId());
        VirtualMachineProduct newProduct = null;
        for( VirtualMachineProduct product : support.listProducts(vm.getProviderMachineImageId()) ) {
            if( product.getProviderProductId().equals(currentProduct.getProviderProductId()) ) {
                continue;
            }
            if( (product.getCpuCount() < currentProduct.getCpuCount()
                    || product.getRamSize().intValue() < currentProduct.getRamSize().intValue())
                    && product.getRootVolumeSize().longValue() < currentProduct.getRootVolumeSize().longValue() ) {
                newProduct = product;
                break;
            }
        }
        assertNotNull("Was unable to select a new product as alterVm target", newProduct);

        if (support.getCapabilities().getVerticalScalingCapabilities().isSupportsProductSizeChanges()) {
            // TODO(stas): Having to pass strings here is not nice, we'll need to fix it in the next release
            vm = support.alterVirtualMachineSize(testVmId, String.valueOf(newProduct.getCpuCount()), String.valueOf(newProduct.getRamSize().intValue()));
        }
        else if (support.getCapabilities().getVerticalScalingCapabilities().isSupportsProductChanges()) {
            vm = support.alterVirtualMachineProduct(testVmId, newProduct.getProviderProductId());
        }
        else {
            tm.warn("Unable to determine how vm product scaling is handled in "+tm.getProvider().getCloudName());
            tm.warn("Test not attempted");
            return;
        }
        if( vm != null ) {
            tm.out("After", vm.getProductId());
            // TODO(stas): I'm not sure this will work in case of the arbitrary memory/cpu changes
            assertEquals("Current product id does not match the target product id", newProduct.getProviderProductId(), vm.getProductId());
        }
    }

    @Test
    public void start() throws CloudException, InternalException {
        assumeTrue(!tm.isTestSkipped());
        ComputeServices services = tm.getProvider().getComputeServices();

        if( services != null ) {
            VirtualMachineSupport support = services.getVirtualMachineSupport();

            if( support != null ) {
                if( testVmId != null ) {
                    VirtualMachine vm = support.getVirtualMachine(testVmId);

                    if( vm != null ) {
                        if( support.getCapabilities().canStart(vm.getCurrentState()) ) {
                            tm.out("Before", vm.getCurrentState());
                            support.start(testVmId);
                            vm = awaitState(vm, VmState.RUNNING, System.currentTimeMillis() + ( CalendarWrapper.MINUTE * 20L ));
                            VmState currentState = ( vm == null ? VmState.TERMINATED : vm.getCurrentState() );
                            tm.out("After", currentState);
                            assertEquals("Current state does not match the target state", VmState.RUNNING, currentState);
                        }
                        else {
                            try {
                                support.start(testVmId);
                                fail("Start/stop is unsupported, yet the method completed without an error");
                            } catch( OperationNotSupportedException expected ) {
                                tm.ok("START -> Operation not supported exception");
                            }
                        }
                    }
                    else {
                        tm.warn("Test virtual machine " + testVmId + " no longer exists");
                    }
                }
                else {
                    tm.warn("No test virtual machine was found for this test");
                }
            }
            else {
                tm.ok("No virtual machine support in this cloud");
            }
        }
        else {
            tm.ok("No compute services in this cloud");
        }
    }

    @Test
    public void pause() throws CloudException, InternalException {
        assumeTrue(!tm.isTestSkipped());
        ComputeServices services = tm.getProvider().getComputeServices();

        if( services != null ) {
            VirtualMachineSupport support = services.getVirtualMachineSupport();

            if( support != null ) {
                if( testVmId != null ) {
                    VirtualMachine vm = support.getVirtualMachine(testVmId);

                    if( vm != null ) {
                        if( support.getCapabilities().canPause(vm.getCurrentState()) ) {
                            tm.out("Before", vm.getCurrentState());
                            support.pause(testVmId);
                            vm = awaitState(vm, VmState.PAUSED, System.currentTimeMillis() + ( CalendarWrapper.MINUTE * 20L ));
                            VmState currentState = ( vm == null ? VmState.TERMINATED : vm.getCurrentState() );
                            tm.out("After", currentState);
                            assertEquals("Current state does not match the target state", VmState.PAUSED, currentState);
                        }
                        else {
                            try {
                                support.pause(testVmId);
                                fail("Pause/unpause is unsupported, yet the method completed without an error");
                            } catch( OperationNotSupportedException expected ) {
                                tm.ok("PAUSE -> Operation not supported exception");
                            }
                        }
                    }
                    else {
                        tm.warn("Test virtual machine " + testVmId + " no longer exists");
                    }
                }
                else {
                    tm.warn("No test virtual machine was found for this test");
                }
            }
            else {
                tm.ok("No virtual machine support in this cloud");
            }
        }
        else {
            tm.ok("No compute services in this cloud");
        }
    }

    @Test
    public void unpause() throws CloudException, InternalException {
        assumeTrue(!tm.isTestSkipped());
        ComputeServices services = tm.getProvider().getComputeServices();

        if( services != null ) {
            VirtualMachineSupport support = services.getVirtualMachineSupport();

            if( support != null ) {
                if( testVmId != null ) {
                    VirtualMachine vm = support.getVirtualMachine(testVmId);

                    if( vm != null ) {
                        if( support.getCapabilities().canUnpause(vm.getCurrentState()) ) {
                            tm.out("Before", vm.getCurrentState());
                            support.unpause(testVmId);
                            vm = awaitState(vm, VmState.RUNNING, System.currentTimeMillis() + ( CalendarWrapper.MINUTE * 20L ));
                            VmState currentState = ( vm == null ? VmState.TERMINATED : vm.getCurrentState() );
                            tm.out("After", currentState);
                            assertEquals("Current state does not match the target state", VmState.RUNNING, currentState);
                        }
                        else {
                            try {
                                support.unpause(testVmId);
                                fail("Pause/unpause is unsupported, yet the method completed without an error");
                            } catch( OperationNotSupportedException expected ) {
                                tm.ok("UNPAUSE -> Operation not supported exception");
                            }
                        }
                    }
                    else {
                        tm.warn("Test virtual machine " + testVmId + " no longer exists");
                    }
                }
                else {
                    tm.warn("No test virtual machine was found for this test");
                }
            }
            else {
                tm.ok("No virtual machine support in this cloud");
            }
        }
        else {
            tm.ok("No compute services in this cloud");
        }
    }

    @Test
    public void suspend() throws CloudException, InternalException {
        assumeTrue(!tm.isTestSkipped());
        ComputeServices services = tm.getProvider().getComputeServices();

        if( services != null ) {
            VirtualMachineSupport support = services.getVirtualMachineSupport();

            if( support != null ) {
                if( testVmId != null ) {
                    VirtualMachine vm = support.getVirtualMachine(testVmId);

                    if( vm != null ) {
                        if( support.getCapabilities().canSuspend(vm.getCurrentState()) ) {
                            tm.out("Before", vm.getCurrentState());
                            support.suspend(testVmId);
                            vm = awaitState(vm, VmState.SUSPENDED, System.currentTimeMillis() + ( CalendarWrapper.MINUTE * 20L ));
                            VmState currentState = ( vm == null ? VmState.TERMINATED : vm.getCurrentState() );
                            tm.out("After", currentState);
                            assertEquals("Current state does not match the target state", VmState.SUSPENDED, currentState);
                        }
                        else {
                            try {
                                support.suspend(testVmId);
                                fail("Suspend/resume is unsupported, yet the method completed without an error");
                            } catch( OperationNotSupportedException expected ) {
                                tm.ok("SUSPEND -> Operation not supported exception");
                            }
                        }
                    }
                    else {
                        tm.warn("Test virtual machine " + testVmId + " no longer exists");
                    }
                }
                else {
                    tm.warn("No test virtual machine was found for this test");
                }
            }
            else {
                tm.ok("No virtual machine support in this cloud");
            }
        }
        else {
            tm.ok("No compute services in this cloud");
        }
    }

    @Test
    public void resume() throws CloudException, InternalException {
        assumeTrue(!tm.isTestSkipped());
        ComputeServices services = tm.getProvider().getComputeServices();

        if( services != null ) {
            VirtualMachineSupport support = services.getVirtualMachineSupport();

            if( support != null ) {
                if( testVmId != null ) {
                    VirtualMachine vm = support.getVirtualMachine(testVmId);

                    if( vm != null ) {
                        if( support.getCapabilities().canResume(vm.getCurrentState()) ) {
                            tm.out("Before", vm.getCurrentState());
                            support.resume(testVmId);
                            vm = awaitState(vm, VmState.RUNNING, System.currentTimeMillis() + ( CalendarWrapper.MINUTE * 20L ));
                            VmState currentState = ( vm == null ? VmState.TERMINATED : vm.getCurrentState() );
                            tm.out("After", currentState);
                            assertEquals("Current state does not match the target state", VmState.RUNNING, currentState);
                        }
                        else {
                            try {
                                support.resume(testVmId);
                                fail("Suspend/resume is unsupported, yet the method completed without an error");
                            } catch( OperationNotSupportedException expected ) {
                                tm.ok("RESUME -> Operation not supported exception");
                            }
                        }
                    }
                    else {
                        tm.warn("Test virtual machine " + testVmId + " no longer exists");
                    }
                }
                else {
                    tm.warn("No test virtual machine was found for this test");
                }
            }
            else {
                tm.ok("No virtual machine support in this cloud");
            }
        }
        else {
            tm.ok("No compute services in this cloud");
        }
    }


    @Test
    public void reboot() throws CloudException, InternalException {
        assumeTrue(!tm.isTestSkipped());
        ComputeServices services = tm.getProvider().getComputeServices();

        if( services != null ) {
            VirtualMachineSupport support = services.getVirtualMachineSupport();

            if( support != null ) {
                if( testVmId != null ) {
                    VirtualMachine vm = support.getVirtualMachine(testVmId);

                    if( vm != null ) {
                        tm.out("Before", vm.getCurrentState());
                        support.reboot(vm.getProviderVirtualMachineId());
                        vm = awaitState(vm, VmState.RUNNING, System.currentTimeMillis() + ( CalendarWrapper.MINUTE * 20L ));
                        VmState currentState = ( vm == null ? VmState.RUNNING : vm.getCurrentState() );
                        tm.out("After", currentState);
                        assertEquals("Current state does not match the target state", VmState.RUNNING, currentState);
                    }
                    else {
                        tm.warn("Test virtual machine " + testVmId + " failed to reboot");
                    }
                }
                else {
                    tm.warn("No test virtual machine was found for this test");
                }
            }
            else {
                tm.ok("No virtual machine support in this cloud");
            }
        }
        else {
            tm.ok("No compute services in this cloud");
        }
    }
    
    @Test
    public void terminate() throws CloudException, InternalException {
        assumeTrue(!tm.isTestSkipped());
        ComputeServices services = tm.getProvider().getComputeServices();

        if( services != null ) {
            VirtualMachineSupport support = services.getVirtualMachineSupport();

            if( support != null ) {
                if( testVmId != null ) {
                    VirtualMachine vm = support.getVirtualMachine(testVmId);

                    if( vm != null ) {
                        tm.out("Before", vm.getCurrentState());
                        support.terminate(vm.getProviderVirtualMachineId());
                        vm = awaitState(vm, VmState.TERMINATED, System.currentTimeMillis() + ( CalendarWrapper.MINUTE * 20L ));
                        VmState currentState = ( vm == null ? VmState.TERMINATED : vm.getCurrentState() );
                        tm.out("After", currentState);
                        assertEquals("Current state does not match the target state", VmState.TERMINATED, currentState);
                    }
                    else {
                        tm.warn("Test virtual machine " + testVmId + " no longer exists");
                    }
                }
                else {
                    tm.warn("No test virtual machine was found for this test");
                }
            }
            else {
                tm.ok("No virtual machine support in this cloud");
            }
        }
        else {
            tm.ok("No compute services in this cloud");
        }
    }
}
