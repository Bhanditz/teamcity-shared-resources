/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package jetbrains.buildServer.sharedResources.server;

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.serverSide.ResolvedSettings;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.sharedResources.server.feature.SharedResourcesFeature;
import jetbrains.buildServer.sharedResources.server.feature.SharedResourcesFeatures;
import jetbrains.buildServer.util.TestFor;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.*;

import static jetbrains.buildServer.sharedResources.server.FeatureParams.LOCKS_FEATURE_PARAM_KEY;

/**
 * Class {@code BuildFeatureParametersProviderTest}
 *
 * Contains tests for {@code BuildFeatureParametersProvider} class
 *
 * @author Oleg Rybak (oleg.rybak@jetbrains.com)
 */
@TestFor(testForClass = BuildFeatureParametersProvider.class)
public class BuildFeatureParametersProviderTest extends BaseTestCase {

  private Mockery myMockery;

  /** Build under test */
  private SBuild myBuild;

  /** BuildType under test */
  private SBuildType myBuildType;

  /** Resolved setting of the build type */
  @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})      // todo: add test for resolved settings
  private ResolvedSettings myResolvedSettings;

  /** SharedResources build feature descriptor */
  private SharedResourcesFeature myFeature;

  /** Build feature parameters with some locks */
  private Map<String, String> myNonEmptyParamMapSomeLocks;

  /** Empty build feature parameters */
  private final Map<String, String> myEmptyParamMap = Collections.emptyMap();

  /** Build feature extractor mock*/
  private SharedResourcesFeatures myFeatures;

  /** Class under test */
  private BuildFeatureParametersProvider myBuildFeatureParametersProvider;



  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myMockery = new Mockery();
    myBuild = myMockery.mock(SBuild.class);
    myBuildType = myMockery.mock(SBuildType.class);
    myFeature = myMockery.mock(SharedResourcesFeature.class);
    myResolvedSettings = myMockery.mock(ResolvedSettings.class);
    myFeatures = myMockery.mock(SharedResourcesFeatures.class);

    myNonEmptyParamMapSomeLocks = new HashMap<String, String>() {{
      put(LOCKS_FEATURE_PARAM_KEY, "lock1 readLock\nlock2 writeLock\nlock3 readLock");
      put("param1_key", "param1_value");
      put("param2_key", "param2_value");
    }};


    myBuildFeatureParametersProvider = new BuildFeatureParametersProvider(myFeatures);
  }

  /**
   * Tests parameters provider when feature is not present
   * @throws Exception if something goes wrong
   */
  @Test
  public void testNoFeaturePresent() throws Exception {
    myMockery.checking(new Expectations() {{
      oneOf(myBuild).getBuildType();
      will(returnValue(myBuildType));

      oneOf(myFeatures).featuresPresent(myBuildType);
      will(returnValue(false));

    }});

    Map<String, String> result = myBuildFeatureParametersProvider.getParameters(myBuild, false);
    assertNotNull(result);
    int size = result.size();
    assertEquals("Expected empty result. Actual size is [" + size + "]" ,0 , size);
    myMockery.assertIsSatisfied();
  }


  /**
   * Test parameters provider when none locks are taken
   * @throws Exception if something goes wrong
   */
  @Test
  public void testEmptyParams() throws Exception {
    final Collection<SharedResourcesFeature> descriptors = new ArrayList<SharedResourcesFeature>() {{
      add(myFeature);
    }};
    myMockery.checking(new Expectations() {{
      oneOf(myBuild).getBuildType();
      will(returnValue(myBuildType));

      oneOf(myFeatures).featuresPresent(myBuildType);
      will(returnValue(true));

      oneOf(myFeatures).searchForResolvedFeatures(myBuildType);
      will(returnValue(descriptors));

      oneOf(myFeature).getBuildParameters();
      will(returnValue(myEmptyParamMap));

    }});

    Map<String, String> result = myBuildFeatureParametersProvider.getParameters(myBuild, false);
    assertNotNull(result);
    int size = result.size();
    assertEquals("Expected empty result. Actual size is [" + size + "]" , 0, size);
    myMockery.assertIsSatisfied();

  }

  /**
   * Test parameters provider when some params are present, though
   * no locks are taken
   * @throws Exception if something goes wrong
   */
  @Test
  public void testNonEmptyParamsNoLocks() throws Exception {
    final Collection<SharedResourcesFeature> descriptors = new ArrayList<SharedResourcesFeature>() {{
      add(myFeature);
    }};
    myMockery.checking(new Expectations() {{
      oneOf(myBuild).getBuildType();
      will(returnValue(myBuildType));

      oneOf(myFeatures).featuresPresent(myBuildType);
      will(returnValue(true));

      oneOf(myFeatures).searchForResolvedFeatures(myBuildType);
      will(returnValue(descriptors));

      oneOf(myFeature).getBuildParameters();
      will(returnValue(myEmptyParamMap));

    }});

    Map<String, String> result = myBuildFeatureParametersProvider.getParameters(myBuild, false);
    assertNotNull(result);
    int size = result.size();
    assertEquals("Expected empty result. Actual size is [" + size + "]" , 0, size);
    myMockery.assertIsSatisfied();
  }


  /**
   * Test parameters provider when some locks are taken
   * @throws Exception if something goes wrong
   */
  @Test
  public void testNonEmptyParamsSomeLocks() throws Exception {
    final Collection<SharedResourcesFeature> descriptors = new ArrayList<SharedResourcesFeature>() {{
      add(myFeature);
    }};
    myMockery.checking(new Expectations() {{
      oneOf(myBuild).getBuildType();
      will(returnValue(myBuildType));

      oneOf(myFeatures).featuresPresent(myBuildType);
      will(returnValue(true));

      oneOf(myFeatures).searchForResolvedFeatures(myBuildType);
      will(returnValue(descriptors));

      oneOf(myFeature).getBuildParameters();
      will(returnValue(myNonEmptyParamMapSomeLocks));

    }});

    Map<String, String> result = myBuildFeatureParametersProvider.getParameters(myBuild, false);
    assertNotNull(result);
    int size = result.size();
    /* Number of locks in non empty param map */
    int numTakenLocks = 3;
    assertEquals("Wrong locks number. Expected [" + numTakenLocks + "]. Actual size is [" + size + "]" , numTakenLocks, size);
    myMockery.assertIsSatisfied();
  }
}
