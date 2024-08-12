package org.jetbrains.idea.maven.dom.converters;

import com.intellij.java.impl.util.xml.converters.values.GenericDomValueConvertersRegistry;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.ide.ServiceManager;
import jakarta.inject.Singleton;
import org.jetbrains.idea.maven.dom.references.MavenPathReferenceConverter;

import java.io.File;
import java.util.Set;

@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
@Singleton
public class MavenDomConvertersRegistry {
    protected GenericDomValueConvertersRegistry myConvertersRegistry;

    private final Set<String> mySoftConverterTypes = Set.of(File.class.getCanonicalName());

    public static MavenDomConvertersRegistry getInstance() {
        return ServiceManager.getService(MavenDomConvertersRegistry.class);
    }

    public MavenDomConvertersRegistry() {
        myConvertersRegistry = new GenericDomValueConvertersRegistry();

        initConverters();
    }

    private void initConverters() {
        myConvertersRegistry.registerDefaultConverters();

        myConvertersRegistry.registerConverter(new MavenPathReferenceConverter(), File.class);
    }

    public GenericDomValueConvertersRegistry getConvertersRegistry() {
        return myConvertersRegistry;
    }

    public boolean isSoft(String type) {
        return mySoftConverterTypes.contains(type);
    }
}
