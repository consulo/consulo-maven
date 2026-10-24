package org.jetbrains.idea.maven.project;

import com.dslplatform.json.CompiledJson;

/**
 * @author VISTALL
 * @since 25/04/2023
 */
@CompiledJson
public enum MaveOverrideCompilerPolicy {
    DISABLED,
    BY_COMPILE,
    BY_PACKAGE
}
