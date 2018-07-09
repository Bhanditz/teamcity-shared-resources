/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package jetbrains.buildServer.sharedResources.pages;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.sharedResources.model.resources.Resource;
import jetbrains.buildServer.sharedResources.server.feature.Resources;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.sharedResources.SharedResourcesPluginConstants.RESOURCE_BY_NAME_COMPARATOR;
import static jetbrains.buildServer.sharedResources.SharedResourcesPluginConstants.toSortedList;

/**
 * Created with IntelliJ IDEA.
 *
 * @author Oleg Rybak (oleg.rybak@jetbrains.com)
 */
public class SharedResourcesBean {

  @NotNull
  private final SProject myProject;

  @NotNull
  private final List<Resource> myOwnResources;

  @NotNull
  private Map<String, List<Resource>> myResourceMap;

  @NotNull
  private Map<String, Resource> myOverridesMap = new HashMap<>();

  @NotNull
  private final Map<String, SProject> myProjects;

  SharedResourcesBean(@NotNull final SProject project,
                      @NotNull final Resources resources,
                      boolean forProjectPage) {
    this(project, resources, forProjectPage, Collections.emptySet());
  }

  SharedResourcesBean(@NotNull final SProject project,
                      @NotNull final Resources resources,
                      boolean forProjectPage,
                      @NotNull final Set<String> available) {
    myProject = project;
    myProjects = myProject.getProjectPath().stream().collect(Collectors.toMap(SProject::getProjectId, Function.identity()));
    if (forProjectPage) {
      myResourceMap = new HashMap<>();
      myOwnResources = resources.getAllOwnResources(project).stream().sorted(RESOURCE_BY_NAME_COMPARATOR).collect(Collectors.toList());
      project.getProjectPath().forEach(p -> {
        final List<Resource> currentOwnResources = resources.getAllOwnResources(p);
        // check that current resource overrides something
        currentOwnResources.forEach(resource -> {
          // check overrides
          checkOverrides(resource, myResourceMap, myOverridesMap);
        });
        currentOwnResources.sort(RESOURCE_BY_NAME_COMPARATOR);
        myResourceMap.put(p.getProjectId(), currentOwnResources);
      });
    } else {
      myOwnResources = resources.getOwnResources(project).stream()
                                .filter(resource -> available.contains(resource.getName()))
                                .sorted(RESOURCE_BY_NAME_COMPARATOR)
                                .collect(Collectors.toList());
      myResourceMap = resources.getResources(project).stream()
                               .filter(resource -> available.contains(resource.getName()))
                               .collect(Collectors.groupingBy(Resource::getProjectId, toSortedList(RESOURCE_BY_NAME_COMPARATOR)));
    }
  }

  private void checkOverrides(final Resource resource,
                              final Map<String, List<Resource>> result,
                              final Map<String, Resource> omap) {
    result.forEach((projectId, resources) -> {
      for (Resource rc : resources) {
        if (resource.getName().equals(rc.getName())) {
          omap.put(rc.getId(), resource);
          break;
        }
      }
    });
  }

  @NotNull
  public List<Resource> getOwnResources() {
    return myOwnResources;
  }

  public Map<String, List<Resource>> getInheritedResources() {
    final Map<String, List<Resource>> result = new LinkedHashMap<>(myResourceMap);
    result.remove(myProject.getProjectId());
    return result;
  }

  @NotNull
  public SProject getProject() {
    return myProject;
  }

  @NotNull
  public List<SProject> getProjectPath() {
    List<SProject> result = myProject.getProjectPath();
    Collections.reverse(result);
    return result;
  }

  @NotNull
  public Collection<Resource> getAllResources() {
    return myResourceMap.values().stream()
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
  }

  @NotNull
  public Map<String, Resource> getOverridesMap() {
    return myOverridesMap;
  }

  @NotNull
  public Map<String, SProject> getProjects() {
    return myProjects;
  }
}
