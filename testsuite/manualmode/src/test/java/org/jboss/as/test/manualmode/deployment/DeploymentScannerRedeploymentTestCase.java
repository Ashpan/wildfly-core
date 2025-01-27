/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
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
 */

package org.jboss.as.test.manualmode.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;

import java.io.File;

import javax.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;

@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class DeploymentScannerRedeploymentTestCase extends AbstractDeploymentScannerBasedTestCase {
    private static final int DELAY = 100;
    private static final int TIMEOUT = 30000;
    private static final PathAddress DEPLOYMENT_TEST = PathAddress.pathAddress(DEPLOYMENT, "deployment-test.jar");

    @Inject
    private ServerController container;

    @Test
    public void testRedeployment() throws Exception {
        container.start();
        try {
            try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()) {

                final File deployment = new File(getDeployDir(), "deployment-test.jar");

                createDeployment(deployment, "non.existing.dependency");

                boolean done = false;
                // Add a new deployment scanner
                addDeploymentScanner(client, 0, false, true);
                try {
                    // Wait until deployed ...
                    long timeout = System.currentTimeMillis() + TimeoutUtil.adjust(TIMEOUT);
                    while (!exists(client, DEPLOYMENT_TEST) && System.currentTimeMillis() < timeout) {
                        Thread.sleep(DELAY);
                    }
                    Assert.assertTrue("deployemt archive is expected.", exists(client, DEPLOYMENT_TEST));
                    Assert.assertEquals("FAILED", deploymentState(client, DEPLOYMENT_TEST));

                    final File deployed = new File(getDeployDir(), "deployment-test.jar.deployed");

                    // Restart ...
                    container.stop();

                    // replace broken deployment with a correct one
                    createDeployment(deployment, "org.jboss.modules", true);

                    startContainer(client);

                    timeout = System.currentTimeMillis() + TimeoutUtil.adjust(TIMEOUT);
                    while (exists(client, DEPLOYMENT_TEST) && System.currentTimeMillis() < timeout) {
                        Thread.sleep(200);
                    }

                    Assert.assertTrue(".deployed marker is expected.", deployed.exists());
                    Assert.assertTrue("deployemt archive is expected.", exists(client, DEPLOYMENT_TEST));
                    Assert.assertEquals("OK", deploymentState(client, DEPLOYMENT_TEST));
                    done = true;

                } finally {
                    try {
                        removeDeploymentScanner(client);
                    } catch (Exception e) {
                        if (done) {
                            //noinspection ThrowFromFinallyBlock
                            throw e;
                        } else {
                            e.printStackTrace(System.out);
                        }
                    }
                }
            }
        } finally {
            container.stop();
        }
    }

    private void startContainer(ModelControllerClient client) throws InterruptedException {

        container.start();

        // Wait until started ...
        long timeout = System.currentTimeMillis() + TimeoutUtil.adjust(TIMEOUT);
        while (!isRunning(client) && System.currentTimeMillis() < timeout) {
            Thread.sleep(200);
        }

    }

}
