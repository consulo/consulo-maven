/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project;

import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.utils.MavenSettings;
import consulo.configurable.Configurable;

public abstract class MavenSettingsTest extends MavenTestCase {
  public void testCloningGeneralSettingsWithoutListeners() throws Exception {
    final String[] log = new String[]{""};

    MavenGeneralSettings s = new MavenGeneralSettings();
    s.addListener(new MavenGeneralSettings.Listener() {
      @Override
      public void changed() {
        log[0] += "changed ";
      }
    });

    s.setMavenBundleName("home");
    assertEquals("changed ", log[0]);

    s.clone().setMavenBundleName("new home");
    assertEquals("changed ", log[0]);
  }

  public void testCloningImportingSettingsWithoutListeners() throws Exception {
    final String[] log = new String[]{""};

    MavenImportingSettings s = new MavenImportingSettings();
    s.addListener(new MavenImportingSettings.Listener() {
      @Override
      public void autoImportChanged() {
      }

      @Override
      public void createModuleGroupsChanged() {
      }

      @Override
      public void createModuleForAggregatorsChanged() {
        log[0] += "changed ";
      }
    });

    s.setCreateModulesForAggregators(true);
    assertEquals("changed ", log[0]);

    s.clone().setCreateModulesForAggregators(false);
    assertEquals("changed ", log[0]);
  }

  public void testImportingSettings() throws Exception {
    assertTrue(new MavenImportingSettings().equals(new MavenImportingSettings()));
    MavenImportingConfigurable importingConfigurable = new MavenImportingConfigurable(myProject);
    importingConfigurable.reset();
    assertFalse(importingConfigurable.isModified());
  }

  public void testNotModifiedAfterCreation() throws Exception {
    MavenSettings s = new MavenSettings(myProject);
    s.createComponent();
    s.reset();
    try {
      assertFalse(s.isModified());
    }
    finally {
      s.disposeUIResources(); //prevent memory leaks
    }

    for (Configurable each : s.getConfigurables()) {
      each.createComponent();
      each.reset();
      try {
        assertFalse(each.isModified());
      }
      finally {
        each.disposeUIResources(); //prevent memory leaks
      }
    }
  }
}
