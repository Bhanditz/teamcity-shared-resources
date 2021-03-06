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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.TLongHashSet;
import gnu.trove.impl.sync.TSynchronizedLongObjectMap;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.sharedResources.SharedResourcesPluginConstants;
import jetbrains.buildServer.sharedResources.model.Lock;
import jetbrains.buildServer.sharedResources.model.LockType;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Class {@code LocksStorageImpl}
 *
 * Implements storage for taken locks during build execution
 *
 * @author Oleg Rybak (oleg.rybak@jetbrains.com)
 */
public class LocksStorageImpl implements LocksStorage {

  @NotNull
  private static final String FILE_NAME = "taken_locks.txt";

  @NotNull
  static final String FILE_PATH = SharedResourcesPluginConstants.BASE_ARTIFACT_PATH + "/" + FILE_NAME; // package visibility for tests

  @NotNull
  private static final Logger log = Logger.getInstance(LocksStorageImpl.class.getName());

  @NotNull
  private static final String MY_ENCODING = "UTF-8";

  /**
   * Contains the set of build ids, that contain taken locks that are stored
   * Added to avoid calling of {@code CacheLoader} for the items that were not stored
   */
  @NotNull
  private final TLongHashSet existsSet = new TLongHashSet();

  /**
   * Stores last N entries of taken locks
   * It is highly unlikely, that we will have more than 300 running builds at the same time
   * Ff we do, data for at least 300 of them will be accessed without accessing artifacts storage
   */
  @NotNull
  private LoadingCache<BuildPromotion, Map<String, Lock>> myLocksCache;

  /**
   * Map with separate guarding lock for each build
   */
  @NotNull
  private final TLongObjectMap<ReentrantLock> myGuards = new TSynchronizedLongObjectMap<>(new TLongObjectHashMap<>());

  public LocksStorageImpl(@NotNull final EventDispatcher<BuildServerListener> dispatcher) {
    CacheLoader<BuildPromotion, Map<String, Lock>> loader = new CacheLoader<BuildPromotion, Map<String, Lock>>() {
      @Override
      public Map<String, Lock> load(@NotNull final BuildPromotion buildPromotion) {
        final Map<String, Lock> result;
        final File artifact = new File(buildPromotion.getArtifactsDirectory(), FILE_PATH);
        if (artifact.exists()) {
          result = new HashMap<>();
          try {
            final String content = FileUtil.readText(artifact, MY_ENCODING);
            final String[] lines = content.split("\\r?\\n");
            for (String line: lines) {
              final Lock lock = deserializeTakenLock(line);
              if (lock != null) {
                result.put(lock.getName(), lock);
              } else {
                if (log.isDebugEnabled()) {
                  log.debug("Wrong locks storage format in file {" + artifact.getAbsolutePath() + "} line: {" + line + "}");
                }
              }
            }
          } catch(IOException e) {
            log.warn("Failed to load taken locks for build [" + buildPromotion + "]; Message is: " + e.getMessage());
          }
        } else {
          result = Collections.emptyMap();
        }
        return result;
      }
    };
    myLocksCache = CacheBuilder.<SBuild, Map<String, Lock>>newBuilder()
            .maximumSize(300) // each entry corresponds to a running build
            .build(loader);

    dispatcher.addListener(new BuildServerAdapter() {

      /**
       * Evicts stored items from cache, as the build is finished and locks are no longer needed
       */
      @Override
      public void buildFinished(@NotNull SRunningBuild build) {
        final ReentrantLock l = myGuards.get(build.getBuildId());
        try {
          if (l != null) {
            l.lock();
          }
          myLocksCache.invalidate(build);
          existsSet.remove(build.getBuildId());
        } finally {
          if (l != null) {
            l.unlock();
          }
        }
      }
    });
  }

  @Override
  public void store(@NotNull final BuildPromotion buildPromotion,
                    @NotNull final Map<Lock, String> takenLocks) {
    if (!takenLocks.isEmpty()) {
      final Long promotionId = buildPromotion.getId();
      final ReentrantLock l = new ReentrantLock(true);
      try {
        l.lock();
        myGuards.put(promotionId, l);
        final Collection<String> serializedStrings = new ArrayList<>();
        Map<String, Lock> locksToStore = new HashMap<>();
        for (Map.Entry<Lock, String> entry: takenLocks.entrySet()) {
          serializedStrings.add(serializeTakenLock(entry.getKey(), entry.getValue()));
          locksToStore.put(entry.getKey().getName(), Lock.createFrom(entry.getKey(), entry.getValue()));
        }
        try {
          final File artifact = new File(buildPromotion.getArtifactsDirectory(), FILE_PATH);
          if (FileUtil.createParentDirs(artifact)) {
            FileUtil.writeFile(artifact, StringUtil.join(serializedStrings, "\n"), MY_ENCODING);
            myLocksCache.put(buildPromotion, locksToStore);
            existsSet.add(promotionId);
          } else {
            log.warn("Failed to create parent dirs for file with taken locks for build {" + buildPromotion + "}");
          }
        } catch (IOException e) {
          log.warn("Failed to store taken locks for build [" + buildPromotion + "]; Message is: " + e.getMessage());
        }
      } finally {
        l.unlock();
        myGuards.remove(promotionId);
      }
    }
  }

  @NotNull
  @Override
  public Map<String, Lock> load(@NotNull final BuildPromotion buildPromotion) {
    final ReentrantLock l = myGuards.get(buildPromotion.getId());
    try {
      if (l != null) {
        l.lock();
      }
      try {
        return myLocksCache.get(buildPromotion);
      } catch (Exception e) {
        log.warn(e);
        return Collections.emptyMap();
      }
    } finally {
      if (l != null) {
        l.unlock();
      }
    }
  }

  @Override
  public boolean locksStored(@NotNull final BuildPromotion buildPromotion) {
    final long id = buildPromotion.getId();
    final ReentrantLock l = myGuards.get(id);
    try {
      if (l != null) {
        l.lock();
      }
      return existsSet.contains(id);
    } finally {
      if (l != null) {
        l.unlock();
      }
    }
  }

  @NotNull
  private String serializeTakenLock(@NotNull final Lock lock, @NotNull final String value) {
    return StringUtil.join("\t", lock.getName(), lock.getType(), value.equals("") ? " " : value);
  }

  @Nullable
  private Lock deserializeTakenLock(@NotNull final String line) {
    final List<String> strings = StringUtil.split(line, true, '\t'); // we need empty values for locks without values
    Lock result = null;
    if (strings.size() == 3) {
      String value =  StringUtil.trim(strings.get(2));
      if (value == null) {
        value = "";
      }
      result = new Lock(strings.get(0), LockType.byName(strings.get(1)), value);
    }
    return result;
  }

}
