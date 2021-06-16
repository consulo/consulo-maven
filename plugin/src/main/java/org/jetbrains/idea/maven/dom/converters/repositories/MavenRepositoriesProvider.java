/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.converters.repositories;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.xmlb.XmlSerializer;
import org.jetbrains.idea.maven.dom.converters.repositories.beans.RepositoriesBean;
import org.jetbrains.idea.maven.dom.converters.repositories.beans.RepositoryBeanInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Serega.Vasiliev
 */
public class MavenRepositoriesProvider {
  public static MavenRepositoriesProvider getInstance() {
    return ServiceManager.getService(MavenRepositoriesProvider.class);
  }

  final Map<String, RepositoryBeanInfo> myRepositoriesMap = new HashMap<String, RepositoryBeanInfo>();

  public MavenRepositoriesProvider() {
    final RepositoriesBean repositoriesBean =
      XmlSerializer.deserialize(MavenRepositoriesProvider.class.getResource("repositories.xml"), RepositoriesBean.class);

    assert repositoriesBean != null;
    RepositoryBeanInfo[] repositories = repositoriesBean.getRepositories();
    assert repositories != null;

    for (RepositoryBeanInfo repository : repositories) {
      registerRepository(repository.getId(), repository);
    }
  }

  public void registerRepository(@Nonnull String id, RepositoryBeanInfo info) {
    myRepositoriesMap.put(id, info);
  }

  @Nonnull
  public Set<String> getRepositoryIds() {
    return myRepositoriesMap.keySet();
  }

  @javax.annotation.Nullable
  public String getRepositoryName(@javax.annotation.Nullable String id) {
    RepositoryBeanInfo pair = myRepositoriesMap.get(id);
    return pair != null ? pair.getName() : null;
  }

  @Nullable
  public String getRepositoryUrl(@Nullable String id) {
    RepositoryBeanInfo pair = myRepositoriesMap.get(id);
    return pair != null ? pair.getUrl() : null;
  }
}
