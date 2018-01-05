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
package org.jetbrains.idea.maven.importing;

import gnu.trove.THashMap;

import java.util.IdentityHashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ArtifactModel;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.elements.PackagingElementResolvingContext;

public abstract class MavenBaseModifiableModelsProvider implements MavenModifiableModelsProvider {
  protected ModifiableModuleModel myModuleModel;
  protected Map<Module, ModifiableRootModel> myRootModels = new THashMap<Module, ModifiableRootModel>();
  protected Map<Library, Library.ModifiableModel> myLibraryModels = new IdentityHashMap<Library, Library.ModifiableModel>();
  protected ModifiableArtifactModel myArtifactModel;
  protected final Project myProject;
  private MavenBaseModifiableModelsProvider.MyPackagingElementResolvingContext myPackagingElementResolvingContext;
  private final ArtifactExternalDependenciesImporter myArtifactExternalDependenciesImporter;

  public MavenBaseModifiableModelsProvider(Project project) {
    myProject = project;
    myArtifactExternalDependenciesImporter = new ArtifactExternalDependenciesImporter();
  }

  @Override
  public ModifiableModuleModel getModuleModel() {
    if (myModuleModel == null) {
      myModuleModel = doGetModuleModel();
    }
    return myModuleModel;
  }

  @Override
  public ModifiableRootModel getRootModel(@NotNull Module module) {
    ModifiableRootModel result = myRootModels.get(module);
    if (result == null) {
      result = doGetRootModel(module);
      myRootModels.put(module, result);
    }
    return result;
  }

  @Override
  public ModifiableArtifactModel getArtifactModel() {
    if (myArtifactModel == null) {
      myArtifactModel = doGetArtifactModel();
    }
    return myArtifactModel;
  }

  @Override
  public PackagingElementResolvingContext getPackagingElementResolvingContext() {
    if (myPackagingElementResolvingContext == null) {
      myPackagingElementResolvingContext = new MyPackagingElementResolvingContext();
    }
    return myPackagingElementResolvingContext;
  }

  @Override
  public ArtifactExternalDependenciesImporter getArtifactExternalDependenciesImporter() {
    return myArtifactExternalDependenciesImporter;
  }

  @Override
  public Library.ModifiableModel getLibraryModel(Library library) {
    Library.ModifiableModel result = myLibraryModels.get(library);
    if (result == null) {
      result = doGetLibraryModel(library);
      myLibraryModels.put(library, result);
    }
    return result;
  }

  @NotNull
  @Override
  public String[] getLibraryUrls(@NotNull Library library, @NotNull OrderRootType type) {
    final Library.ModifiableModel model = myLibraryModels.get(library);
    if (model != null) {
      return model.getUrls(type);
    }
    return library.getUrls(type);
  }

  protected abstract ModifiableArtifactModel doGetArtifactModel();

  protected abstract ModifiableModuleModel doGetModuleModel();

  protected abstract ModifiableRootModel doGetRootModel(Module module);

  protected abstract Library.ModifiableModel doGetLibraryModel(Library library);

  @Override
  public Module[] getModules() {
    return getModuleModel().getModules();
  }

  protected void processExternalArtifactDependencies() {
    myArtifactExternalDependenciesImporter.applyChanges(getArtifactModel(), getPackagingElementResolvingContext());
  }

  @Override
  public VirtualFile[] getContentRoots(Module module) {
    return getRootModel(module).getContentRoots();
  }

  private class MyPackagingElementResolvingContext implements PackagingElementResolvingContext {
    private final ModulesProvider myModulesProvider = new MavenModulesProvider();

    @Override
	@NotNull
    public Project getProject() {
      return myProject;
    }

    @Override
	@NotNull
    public ArtifactModel getArtifactModel() {
      return MavenBaseModifiableModelsProvider.this.getArtifactModel();
    }

    @Override
	@NotNull
    public ModulesProvider getModulesProvider() {
      return myModulesProvider;
    }

    @Override
	public Library findLibrary(@NotNull String level, @NotNull String libraryName) {
      if (level.equals(LibraryTablesRegistrar.PROJECT_LEVEL)) {
        return getLibraryByName(libraryName);
      }
      final LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(level, myProject);
      return table != null ? table.getLibraryByName(libraryName) : null;
    }
  }

  private class MavenModulesProvider implements ModulesProvider {
    @Override
	@NotNull
    public Module[] getModules() {
      return getModuleModel().getModules();
    }

    @Override
	public Module getModule(String name) {
      return getModuleModel().findModuleByName(name);
    }

    @Override
	public ModuleRootModel getRootModel(@NotNull Module module) {
      return MavenBaseModifiableModelsProvider.this.getRootModel(module);
    }
  }
}
