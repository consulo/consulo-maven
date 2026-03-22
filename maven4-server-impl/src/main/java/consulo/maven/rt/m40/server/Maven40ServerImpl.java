package consulo.maven.rt.m40.server;

import consulo.maven.rt.m40.server.utils.Maven40ModelConverter;
import consulo.maven.rt.server.common.model.*;
import consulo.maven.rt.server.common.server.*;
import org.apache.maven.model.*;
import org.apache.maven.model.building.ModelProblemCollectorRequest;
import org.apache.maven.model.inheritance.DefaultInheritanceAssembler;
import org.apache.maven.model.path.DefaultModelPathTranslator;
import org.apache.maven.model.path.DefaultPathTranslator;
import org.apache.maven.model.path.ProfileActivationFilePathInterpolator;
import org.apache.maven.model.profile.DefaultProfileActivationContext;
import org.apache.maven.model.profile.DefaultProfileInjector;
import org.apache.maven.model.profile.DefaultProfileSelector;
import org.apache.maven.model.profile.activation.*;
import org.apache.maven.model.root.RootLocator;

import java.io.File;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class Maven40ServerImpl extends MavenRemoteObject implements MavenServer {
  @Override
  public void set(MavenServerLogger logger, MavenServerDownloadListener downloadListener) throws RemoteException {
    // no globals for Maven 4
  }

  @Override
  public MavenServerEmbedder createEmbedder(MavenServerSettings settings) throws RemoteException {
    try {
      Maven40ServerEmbedderImpl result = new Maven40ServerEmbedderImpl(settings);
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (Exception e) {
      throw rethrowException(e);
    }
  }

  @Override
  public MavenServerIndexer createIndexer() throws RemoteException {
    Maven40ServerIndexer indexer = new Maven40ServerIndexer();
    UnicastRemoteObject.exportObject(indexer, 0);
    return indexer;
  }

  @Override
  public MavenModel interpolateAndAlignModel(MavenModel model, File basedir) throws RemoteException {
    try {
      Model nativeModel = toNativeModel(model);

      DefaultModelPathTranslator pathTranslator = new DefaultModelPathTranslator();
      pathTranslator.setPathTranslator(new DefaultPathTranslator());
      pathTranslator.alignToBaseDirectory(nativeModel, basedir, null);

      return Maven40ModelConverter.convertModel(basedir, nativeModel);
    }
    catch (Exception e) {
      throw rethrowException(e);
    }
  }

  @Override
  public MavenModel assembleInheritance(MavenModel model, MavenModel parentModel) throws RemoteException {
    try {
      Model nativeModel = toNativeModel(model);
      new DefaultInheritanceAssembler().assembleModelInheritance(nativeModel, toNativeModel(parentModel), null, r -> {});
      return Maven40ModelConverter.convertModel(null, nativeModel);
    }
    catch (Exception e) {
      throw rethrowException(e);
    }
  }

  @Override
  public ProfileApplicationResult applyProfiles(MavenModel model,
                                                  File basedir,
                                                  MavenExplicitProfiles explicitProfiles,
                                                  Collection<String> alwaysOnProfiles) throws RemoteException {
    try {
      Model nativeModel = toNativeModel(model);

      Collection<String> enabledProfiles = explicitProfiles.getEnabledProfiles();
      Collection<String> disabledProfiles = explicitProfiles.getDisabledProfiles();

      DefaultProfileActivationContext context = new DefaultProfileActivationContext();
      context.setActiveProfileIds(new ArrayList<>(enabledProfiles));
      context.setInactiveProfileIds(new ArrayList<>(disabledProfiles));
      context.setSystemProperties(System.getProperties());
      if (basedir != null) {
        context.setProjectDirectory(basedir);
      }

      DefaultPathTranslator pathTranslator = new DefaultPathTranslator();
      ProfileActivationFilePathInterpolator filePathInterpolator = new ProfileActivationFilePathInterpolator();
      filePathInterpolator.setPathTranslator(pathTranslator);
      filePathInterpolator.setRootLocator(new RootLocator() {
        @Override
        public boolean isRootDirectory(Path path) {
          return false;
        }
      });

      DefaultProfileSelector selector = new DefaultProfileSelector();
      selector.addProfileActivator(new JdkVersionProfileActivator());
      selector.addProfileActivator(new OperatingSystemProfileActivator());
      selector.addProfileActivator(new PropertyProfileActivator());
      FileProfileActivator fileActivator = new FileProfileActivator();
      fileActivator.setProfileActivationFilePathInterpolator(filePathInterpolator);
      selector.addProfileActivator(fileActivator);

      List<Profile> rawProfiles = nativeModel.getProfiles();
      List<Profile> deactivatedProfiles = new ArrayList<>();

      // Profiles explicitly disabled
      for (Profile p : rawProfiles) {
        if (disabledProfiles.contains(p.getId())) {
          deactivatedProfiles.add(p);
        }
      }

      // Use selector to find active profiles (handles explicit, always-on, and condition-based)
      List<Profile> filteredProfiles = new ArrayList<>();
      for (Profile p : rawProfiles) {
        if (!disabledProfiles.contains(p.getId())) {
          filteredProfiles.add(p);
        }
      }

      // Manually add alwaysOnProfiles to the active list since selector may not know about them
      List<Profile> extraActive = new ArrayList<>();
      Set<String> selectorEnabledIds = new HashSet<>(enabledProfiles);
      for (Profile p : filteredProfiles) {
        if (alwaysOnProfiles.contains(p.getId()) && !selectorEnabledIds.contains(p.getId())) {
          extraActive.add(p);
        }
      }

      List<Profile> selectorActivated = selector.getActiveProfiles(filteredProfiles, context, r -> {});

      Set<String> activatedIds = new HashSet<>();
      List<Profile> activatedProfiles = new ArrayList<>();
      for (Profile p : selectorActivated) {
        if (activatedIds.add(p.getId())) {
          activatedProfiles.add(p);
        }
      }
      for (Profile p : extraActive) {
        if (activatedIds.add(p.getId())) {
          activatedProfiles.add(p);
        }
      }

      DefaultProfileInjector injector = new DefaultProfileInjector();
      for (Profile each : activatedProfiles) {
        injector.injectProfile(nativeModel, each, null, r -> {});
      }

      MavenModel resultModel = Maven40ModelConverter.convertModel(basedir, nativeModel);
      return new ProfileApplicationResult(resultModel,
                                          new MavenExplicitProfiles(collectProfilesIds(activatedProfiles),
                                                                    collectProfilesIds(deactivatedProfiles)));
    }
    catch (Exception e) {
      throw rethrowException(e);
    }
  }

  private static Collection<String> collectProfilesIds(List<Profile> profiles) {
    Set<String> result = new HashSet<>();
    for (Profile each : profiles) {
      if (each.getId() != null) {
        result.add(each.getId());
      }
    }
    return result;
  }

  // Convert Consulo MavenModel to native org.apache.maven.model.Model
  static Model toNativeModel(MavenModel model) {
    Model result = new Model();
    if (model.getMavenId() != null) {
      result.setArtifactId(model.getMavenId().getArtifactId());
      result.setGroupId(model.getMavenId().getGroupId());
      result.setVersion(model.getMavenId().getVersion());
    }
    result.setPackaging(model.getPackaging());
    result.setName(model.getName());

    if (model.getParent() != null) {
      Parent parent = new Parent();
      parent.setArtifactId(model.getParent().getMavenId().getArtifactId());
      parent.setGroupId(model.getParent().getMavenId().getGroupId());
      parent.setVersion(model.getParent().getMavenId().getVersion());
      parent.setRelativePath(model.getParent().getRelativePath());
      result.setParent(parent);
    }

    result.setModules(model.getModules());
    if (model.getProperties() != null) {
      result.setProperties(model.getProperties());
    }

    Build build = new Build();
    MavenBuild modelBuild = model.getBuild();
    build.setDirectory(modelBuild.getDirectory());
    build.setOutputDirectory(modelBuild.getOutputDirectory());
    build.setTestOutputDirectory(modelBuild.getTestOutputDirectory());
    build.setFinalName(modelBuild.getFinalName());
    build.setDefaultGoal(modelBuild.getDefaultGoal());

    List<String> sources = modelBuild.getSources();
    if (sources.size() == 1) {
      build.setSourceDirectory(sources.get(0));
    }
    List<String> testSources = modelBuild.getTestSources();
    if (testSources.size() == 1) {
      build.setTestSourceDirectory(testSources.get(0));
    }
    result.setBuild(build);

    result.setProfiles(toNativeProfiles(model.getProfiles()));

    return result;
  }

  private static List<Profile> toNativeProfiles(List<MavenProfile> profiles) {
    List<Profile> result = new ArrayList<>(profiles.size());
    for (MavenProfile each : profiles) {
      Profile p = new Profile();
      p.setId(each.getId());
      p.setSource(each.getSource());
      p.setActivation(toNativeActivation(each.getActivation()));
      if (each.getProperties() != null) {
        p.setProperties(each.getProperties());
      }
      p.setModules(each.getModules());
      result.add(p);
    }
    return result;
  }

  private static Activation toNativeActivation(MavenProfileActivation activation) {
    if (activation == null) return null;
    Activation result = new Activation();
    result.setActiveByDefault(activation.isActiveByDefault());
    result.setJdk(activation.getJdk());
    if (activation.getOs() != null) {
      ActivationOS os = new ActivationOS();
      os.setArch(activation.getOs().getArch());
      os.setFamily(activation.getOs().getFamily());
      os.setName(activation.getOs().getName());
      os.setVersion(activation.getOs().getVersion());
      result.setOs(os);
    }
    if (activation.getFile() != null) {
      ActivationFile file = new ActivationFile();
      file.setExists(activation.getFile().getExists());
      file.setMissing(activation.getFile().getMissing());
      result.setFile(file);
    }
    if (activation.getProperty() != null) {
      ActivationProperty property = new ActivationProperty();
      property.setName(activation.getProperty().getName());
      property.setValue(activation.getProperty().getValue());
      result.setProperty(property);
    }
    return result;
  }

  @Override
  public synchronized void unreferenced() {
    System.exit(0);
  }
}
