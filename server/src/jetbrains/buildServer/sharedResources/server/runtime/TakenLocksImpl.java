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

package jetbrains.buildServer.sharedResources.server.runtime;

import jetbrains.buildServer.serverSide.BuildPromotionEx;
import jetbrains.buildServer.serverSide.RunningBuildEx;
import jetbrains.buildServer.serverSide.buildDistribution.BuildPromotionInfo;
import jetbrains.buildServer.serverSide.buildDistribution.QueuedBuildInfo;
import jetbrains.buildServer.serverSide.buildDistribution.RunningBuildInfo;
import jetbrains.buildServer.sharedResources.model.Lock;
import jetbrains.buildServer.sharedResources.model.TakenLock;
import jetbrains.buildServer.sharedResources.model.resources.CustomResource;
import jetbrains.buildServer.sharedResources.model.resources.QuotedResource;
import jetbrains.buildServer.sharedResources.model.resources.Resource;
import jetbrains.buildServer.sharedResources.model.resources.ResourceType;
import jetbrains.buildServer.sharedResources.server.feature.Locks;
import jetbrains.buildServer.sharedResources.server.feature.Resources;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Oleg Rybak (oleg.rybak@jetbrains.com)
 */
public class TakenLocksImpl implements TakenLocks {

  @NotNull
  private final Locks myLocks;

  @NotNull
  private final Resources myResources;

  @NotNull
  private final LocksStorage myLocksStorage;

  public TakenLocksImpl(@NotNull final Locks locks,
                        @NotNull final Resources resources,
                        @NotNull final LocksStorage locksStorage) {
    myLocks = locks;
    myResources = resources;
    myLocksStorage = locksStorage;
  }

  @NotNull
  @Override
  public Map<String, TakenLock> collectTakenLocks(@NotNull final String projectId,
                                                  @NotNull final Collection<RunningBuildInfo> runningBuilds,
                                                  @NotNull final Collection<QueuedBuildInfo> queuedBuilds) {
    final Map<String, TakenLock> result = new HashMap<String, TakenLock>();
    for (RunningBuildInfo runningBuildInfo: runningBuilds) {
      BuildPromotionEx bpEx = (BuildPromotionEx)runningBuildInfo.getBuildPromotionInfo();
      if (projectId.equals(bpEx.getProjectId())) {
        Collection<Lock> locks;
        RunningBuildEx rbEx = (RunningBuildEx)runningBuildInfo;
        if (myLocksStorage.locksStored(rbEx)) {
          locks = myLocksStorage.load(rbEx).keySet();
        } else {
          locks = getLocksFromPromotion(bpEx);
        }
        addToTakenLocks(result, bpEx, locks);
      }
    }
    for (QueuedBuildInfo info: queuedBuilds) {
      BuildPromotionEx bpEx = (BuildPromotionEx)info.getBuildPromotionInfo();
      if (projectId.equals(bpEx.getProjectId())) {
        Collection<Lock> locks = getLocksFromPromotion(bpEx);
        addToTakenLocks(result, bpEx, locks);
      }
    }
    return result;

  }

  private void addToTakenLocks(@NotNull final Map<String, TakenLock> takenLocks,
                               @NotNull final BuildPromotionInfo bpInfo,
                               @NotNull final Collection<Lock> locks) {
    for (Lock lock: locks) {
      TakenLock takenLock = takenLocks.get(lock.getName());
      if (takenLock == null) {
        takenLock = new TakenLock();
        takenLocks.put(lock.getName(), takenLock);
      }
      takenLock.addLock(bpInfo, lock);
    }
  }

  private Collection<Lock> getLocksFromPromotion(@NotNull final BuildPromotionInfo buildPromotion) {
    return myLocks.fromBuildParameters(((BuildPromotionEx)buildPromotion).getParametersProvider().getAll());
  }

  @NotNull
  @Override
  public Collection<Lock> getUnavailableLocks(@NotNull Collection<Lock> locksToTake,
                                              @NotNull Map<String, TakenLock> takenLocks,
                                              @NotNull String projectId) {
    final Map<String, Resource> resources = myResources.asMap(projectId);
    final Collection<Lock> result = new ArrayList<Lock>();
    for (Lock lock : locksToTake) {
      final TakenLock takenLock = takenLocks.get(lock.getName());
      if (takenLock != null) {
        switch (lock.getType())  {
          case READ:
            // 1) Check that no write lock exists
            if (takenLock.hasWriteLocks()) {
              result.add(lock);
            }
            // check against resource
            final Resource resource = resources.get(lock.getName());
            if (resource != null) { // supporting only quoted resources for now
              if (ResourceType.QUOTED.equals(resource.getType())) {
                QuotedResource qRes = (QuotedResource)resource;
                if (!qRes.isInfinite()) {
                  if (takenLock.getReadLocks().size() >= qRes.getQuota()) {
                    result.add(lock);
                  }
                }
              } else if (ResourceType.CUSTOM.equals(resource.getType())) {
                CustomResource cRes = (CustomResource)resource;
                if (takenLock.getReadLocks().size() >= cRes.getValues().size()) {
                  result.add(lock);
                }
              }
            }
            break;
          case WRITE:
            if (takenLock.hasReadLocks() || takenLock.hasWriteLocks()) { // if anyone is accessing the resource
              result.add(lock);
            }
        }
      }
    }
    return result;
  }
}