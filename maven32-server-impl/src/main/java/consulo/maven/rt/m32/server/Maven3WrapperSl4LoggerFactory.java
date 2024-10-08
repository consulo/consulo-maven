// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.maven.rt.m32.server;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

public class Maven3WrapperSl4LoggerFactory implements ILoggerFactory {

    public Maven3WrapperSl4LoggerFactory() {
    }

    @Override
    public Logger getLogger(String s) {
        return new Maven3Sl4jLoggerWrapper(s);
    }
}
