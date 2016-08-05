/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package jetbrains.buildServer.sharedResources.server.project;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.SProjectFeatureDescriptor;
import jetbrains.buildServer.sharedResources.SharedResourcesPluginConstants;
import jetbrains.buildServer.sharedResources.model.resources.Resource;
import jetbrains.buildServer.sharedResources.model.resources.ResourceFactory;
import jetbrains.buildServer.util.CollectionsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.sharedResources.SharedResourcesPluginConstants.FEATURE_TYPE;
import static jetbrains.buildServer.sharedResources.SharedResourcesPluginConstants.ProjectFeatureParameters.NAME;
import static jetbrains.buildServer.sharedResources.SharedResourcesPluginConstants.RESOURCE_NAMES_COMPARATOR;

/**
 * Created with IntelliJ IDEA.
 *
 * @author Oleg Rybak (oleg.rybak@jetbrains.com)
 */
public class ResourceProjectFeaturesImpl implements ResourceProjectFeatures {

  @Override
  public void addResource(@NotNull final SProject project,
                          @NotNull final Map<String, String> resourceParameters) {
    project.addFeature(SharedResourcesPluginConstants.FEATURE_TYPE, resourceParameters);
  }

  @Override
  public void deleteResource(@NotNull final SProject project, @NotNull final String id) {
    final SProjectFeatureDescriptor descriptor = getFeatureById(project, id);
    if (descriptor != null) {
      project.removeFeature(descriptor.getId());
    }
  }

  public void editResource(@NotNull final SProject project,
                           @NotNull final String id,
                           @NotNull final Map<String, String> parameters) {
    final SProjectFeatureDescriptor descriptor = getFeatureById(project, id);
    if (descriptor != null) {
      project.updateFeature(id, FEATURE_TYPE, parameters);
    }
  }

  @Nullable
  private SProjectFeatureDescriptor getFeatureById(final @NotNull SProject project, final @NotNull String id) {
    return project.getOwnFeaturesOfType(SharedResourcesPluginConstants.FEATURE_TYPE).stream()
                  .filter(fd -> id.equals(fd.getId()))
                  .findFirst()
                  .orElse(null);
  }

  @NotNull
  @Override
  public Map<SProject, Map<String, Resource>> asProjectResourceMap(@NotNull final SProject project) {
    final Map<SProject, Map<String, Resource>> result = new LinkedHashMap<>();
    final Map<String, Resource> treeResources = new HashMap<>(); // todo: we need only names here
    final List<SProject> path = project.getProjectPath();
    final ListIterator<SProject> it = path.listIterator(path.size());
    while (it.hasPrevious()) {
      SProject p = it.previous();
      Map<String, Resource> currentResources = getResourcesForProject(p);
      final Map<String, Resource> value = CollectionsUtil.filterMapByKeys(
        currentResources, data -> !treeResources.containsKey(data)
      );
      treeResources.putAll(value);
      result.put(p, value);
    }
    return result;
  }

  @Override
  @NotNull
  public Map<String, Resource> asMap(@NotNull final SProject project) {
    final Map<String, Resource> result = new TreeMap<>(RESOURCE_NAMES_COMPARATOR);
    final List<SProject> path = project.getProjectPath();
    final ListIterator<SProject> it = path.listIterator(path.size());
    while (it.hasPrevious()) {
      SProject p = it.previous();
      Map<String, Resource> currentResources = getResourcesForProject(p);
      result.putAll(CollectionsUtil.filterMapByKeys(currentResources, data -> !result.containsKey(data)));
    }
    return result;
  }

  @NotNull
  private Map<String, Resource> getResourcesForProject(@NotNull final SProject project) {
    final Map<String, Resource> result = new TreeMap<>(RESOURCE_NAMES_COMPARATOR);
    result.putAll(getResourceFeatures(project).stream()
                                              .map(ResourceFactory::fromDescriptor)
                                              .filter(Objects::nonNull)
                                              .collect(
                                                Collectors.toMap(
                                                  Resource::getName,
                                                  Function.identity(),
                                                  (r1, r2) -> r1)
                                              ));
    return result;
  }

  @NotNull
  @Override
  public List<ResourceProjectFeature> getOwnFeatures(@NotNull final SProject project) {
    return project.getOwnFeaturesOfType(SharedResourcesPluginConstants.FEATURE_TYPE).stream()
                  .map(ResourceProjectFeatureImpl::new)
                  .collect(Collectors.toList());
  }

  @NotNull
  private Collection<SProjectFeatureDescriptor> getResourceFeatures(@NotNull SProject project) {
    return project.getOwnFeaturesOfType(SharedResourcesPluginConstants.FEATURE_TYPE);
  }
}
