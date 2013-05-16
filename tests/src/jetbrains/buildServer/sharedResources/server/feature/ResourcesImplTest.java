package jetbrains.buildServer.sharedResources.server.feature;

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.SecurityContextEx;
import jetbrains.buildServer.serverSide.settings.ProjectSettingsManager;
import jetbrains.buildServer.sharedResources.SharedResourcesPluginConstants;
import jetbrains.buildServer.sharedResources.TestUtils;
import jetbrains.buildServer.sharedResources.model.resources.Resource;
import jetbrains.buildServer.sharedResources.model.resources.ResourceFactory;
import jetbrains.buildServer.sharedResources.settings.PluginProjectSettings;
import jetbrains.buildServer.util.TestFor;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 *
 * @author Oleg Rybak (oleg.rybak@jetbrains.com)
 */
@SuppressWarnings("UnusedShould")
@TestFor(testForClass = {Resources.class, ResourcesImpl.class})
public class ResourcesImplTest extends BaseTestCase {

  private Mockery m;

  private ProjectSettingsManager myProjectSettingsManager;

  private PluginProjectSettings myPluginProjectSettings;

  private ProjectManager myProjectManager;

  private SProject myProject;

  private SecurityContextEx mySecurityContextEx;

  /**
   * Class under test
   */
  private ResourcesImpl resources;

  final String projectId = TestUtils.generateRandomName();

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    m = new Mockery() {{
      setImposteriser(ClassImposteriser.INSTANCE);
    }};

    myProjectSettingsManager = m.mock(ProjectSettingsManager.class);
    myPluginProjectSettings = m.mock(PluginProjectSettings.class);
    myProjectManager = m.mock(ProjectManager.class);
    mySecurityContextEx = m.mock(SecurityContextEx.class);
    myProject = m.mock(SProject.class);
    resources = new ResourcesImpl(myProjectSettingsManager, myProjectManager, mySecurityContextEx);
  }

  @Test
  public void testAddResource() {
    final Resource resource = ResourceFactory.newQuotedResource("resource1", 1);
    m.checking(new Expectations() {{
      oneOf(myProjectSettingsManager).getSettings(projectId, SharedResourcesPluginConstants.SERVICE_NAME);
      will(returnValue(myPluginProjectSettings));

      oneOf(myPluginProjectSettings).addResource(resource);
    }});

    resources.addResource(projectId, resource);
    m.assertIsSatisfied();
  }

  @Test
  public void testDeleteResource() {
    final String name = "myName1";
    m.checking(new Expectations() {{
      oneOf(myProjectSettingsManager).getSettings(projectId, SharedResourcesPluginConstants.SERVICE_NAME);
      will(returnValue(myPluginProjectSettings));

      oneOf(myPluginProjectSettings).deleteResource(name);
    }});

    resources.deleteResource(projectId, name);
    m.assertIsSatisfied();
  }

  @Test
  public void testEditResource() {
    final String name = "myName1";
    final Resource resource = ResourceFactory.newQuotedResource("resource1", 1);

    m.checking(new Expectations() {{
      oneOf(myProjectSettingsManager).getSettings(projectId, SharedResourcesPluginConstants.SERVICE_NAME);
      will(returnValue(myPluginProjectSettings));

      oneOf(myPluginProjectSettings).editResource(name, resource);
    }});

    resources.editResource(projectId, name, resource);
    m.assertIsSatisfied();
  }

  @Test
  public void testAsMap() {
    final Map<String, Resource> resourceMap = new HashMap<String, Resource>();
    resourceMap.put("r1", ResourceFactory.newQuotedResource("r1", 1));
    resourceMap.put("r2", ResourceFactory.newInfiniteResource("r2"));

    m.checking(new Expectations() {{
      oneOf(myProjectSettingsManager).getSettings(projectId, SharedResourcesPluginConstants.SERVICE_NAME);
      will(returnValue(myPluginProjectSettings));

      oneOf(myPluginProjectSettings).getResourceMap();
      will(returnValue(resourceMap));

      oneOf(myProjectManager).findProjectById(projectId);
      will(returnValue(myProject));

      oneOf(myProject).getParentProjectId();
      will(returnValue(null));
    }});

    final Map<String, Resource> result = resources.asMap(projectId);
    assertNotNull(result);
    assertEquals(resourceMap.size(), result.size());
  }

  @Test
  public void testGetAllResources() throws Throwable {
    final Map<String, Resource> resourceMap = new HashMap<String, Resource>();
    resourceMap.put("r1", ResourceFactory.newQuotedResource("r1", 1));
    resourceMap.put("r2", ResourceFactory.newInfiniteResource("r2"));
    resourceMap.put("r3", ResourceFactory.newCustomResource("r3", Arrays.asList("val1", "val2", "val3")));

    final SProject rootProject = m.mock(SProject.class, "<ROOT>");
    final PluginProjectSettings rootProjectSettings = m.mock(PluginProjectSettings.class, "rootProjectSettings");


    m.checking(new Expectations() {{

      oneOf(myProjectManager).getRootProject();
      will(returnValue(rootProject));

      exactly(1).of(same(mySecurityContextEx)).method("runAsSystem");
      will(returnValue(Arrays.asList(myProject)));

      oneOf(rootProject).getProjectId();
      will(returnValue("<ROOT>"));

      oneOf(myProjectSettingsManager).getSettings("<ROOT>", SharedResourcesPluginConstants.SERVICE_NAME);
      will(returnValue(rootProjectSettings));

      oneOf(rootProjectSettings).getResourceMap();
      will(returnValue(Collections.emptyMap()));

      oneOf(myProject).getProjectId();
      will(returnValue(projectId));

      oneOf(myProjectSettingsManager).getSettings(projectId, SharedResourcesPluginConstants.SERVICE_NAME);
      will(returnValue(myPluginProjectSettings));

      oneOf(myPluginProjectSettings).getResourceMap();
      will(returnValue(resourceMap));


    }});

    final Map<String, Resource> result = resources.getAllResources();
    assertNotNull(result);
    m.assertIsSatisfied();
  }
}