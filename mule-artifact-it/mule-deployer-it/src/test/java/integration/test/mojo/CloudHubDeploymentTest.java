/*
 * Mule ESB Maven Tools
 * <p>
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * <p>
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package integration.test.mojo;

import static com.google.common.collect.Sets.newHashSet;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;
import static org.mule.tools.client.AbstractMuleClient.DEFAULT_BASE_URL;
import static org.mule.tools.client.cloudhub.CloudhubClient.STARTED_STATUS;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mule.tools.client.cloudhub.Application;
import org.mule.tools.client.cloudhub.CloudhubClient;
import org.mule.tools.client.cloudhub.OperationRetrier;
import org.mule.tools.client.cloudhub.OperationRetrier.RetriableOperation;
import org.mule.tools.client.standalone.controller.probing.deployment.ApplicationDeploymentProbe;
import org.mule.tools.client.standalone.controller.probing.deployment.DeploymentProbe;
import org.mule.tools.client.standalone.controller.probing.deployment.DomainDeploymentProbe;
import org.mule.tools.client.standalone.exception.DeploymentException;
import org.mule.tools.model.anypoint.CloudHubDeployment;

@RunWith(Parameterized.class)
public class CloudHubDeploymentTest extends AbstractDeploymentTest {

  private static final long RETRY_SLEEP_TIME = 30000;

  private static final int APPLICATION_NAME_LENGTH = 10;
  private static final String APPLICATION = "empty-mule-deploy-cloudhub-project";
  private static final String APPLICATION_NAME = randomAlphabetic(APPLICATION_NAME_LENGTH).toLowerCase();
  private static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";

  private Verifier verifier;
  private CloudhubClient cloudhubClient;

  @Parameterized.Parameters
  public static Iterable<? extends Object> data() {
    return Arrays.asList("4.0.0", "4.1.0-SNAPSHOT");
  }

  private String muleVersion;

  public CloudHubDeploymentTest(String muleVersion) {
    this.muleVersion = muleVersion;
  }

  public String getApplication() {
    return APPLICATION;
  }

  @Before
  public void before() throws VerificationException, InterruptedException, IOException {
    log.info("Initializing context...");

    verifier = buildBaseVerifier();
    verifier.setEnvironmentVariable("username", username);
    verifier.setEnvironmentVariable("password", password);
    verifier.setEnvironmentVariable("environment", PRODUCTION_ENVIRONMENT);
    verifier.setEnvironmentVariable("mule.version", muleVersion);
    verifier.setEnvironmentVariable("cloudhub.application.name", APPLICATION_NAME);

    verifier.setSystemProperty("cloudhub.deployer.validate.application.started", "true");

    cloudhubClient = getCloudHubClient();
  }

  @Test
  public void testCloudHubDeploy() throws VerificationException, InterruptedException, TimeoutException, DeploymentException {
    String version = muleVersion.replace(SNAPSHOT_SUFFIX, "");
    assumeTrue("Version not supported by CloudHub", cloudhubClient.getSupportedMuleVersions().contains(version));

    log.info("Executing mule:deploy goal...");
    verifier.addCliOption("-DmuleDeploy");
    verifier.executeGoal(DEPLOY_GOAL);

    String status = validateApplicationIsInStatus(APPLICATION_NAME, STARTED_STATUS);
    assertThat("Application was not deployed", status, is(STARTED_STATUS));

    verifier.verifyErrorFreeLog();
  }

  @After
  public void tearDown() {
    cloudhubClient.deleteApplication(APPLICATION_NAME);
  }

  private CloudhubClient getCloudHubClient() {
    CloudHubDeployment cloudHubDeployment = new CloudHubDeployment();
    cloudHubDeployment.setUsername(username);
    cloudHubDeployment.setPassword(password);

    cloudHubDeployment.setUri(DEFAULT_BASE_URL);
    cloudHubDeployment.setEnvironment(PRODUCTION_ENVIRONMENT);
    cloudHubDeployment.setBusinessGroup("");

    CloudhubClient cloudhubClient = new CloudhubClient(cloudHubDeployment, null);
    cloudhubClient.init();

    return cloudhubClient;
  }

  private String validateApplicationIsInStatus(String applicationName, String status)
      throws DeploymentException, TimeoutException, InterruptedException {
    log.debug("Checking application " + applicationName + " for status " + status + "...");

    ApplicationStatusRetriableOperation operation = new ApplicationStatusRetriableOperation(status, applicationName);

    OperationRetrier operationRetrier = new OperationRetrier();
    operationRetrier.setSleepTime(RETRY_SLEEP_TIME);

    operationRetrier.retry(operation);

    return operation.getApplicationStatus();

  }

  class ApplicationStatusRetriableOperation implements RetriableOperation {

    private String applicationStatus;

    private String expectedStatus;
    private String applicationName;

    public ApplicationStatusRetriableOperation(String expectedStatus, String applicationName) {
      this.expectedStatus = expectedStatus;
      this.applicationName = applicationName;
    }

    public String getApplicationStatus() {
      return applicationStatus;
    }

    @Override
    public Boolean run() {
      Application application = cloudhubClient.getApplication(applicationName);
      applicationStatus = application.status;
      if (application != null && expectedStatus.equals(application.status)) {
        return false;
      }
      return true;
    }
  }
}