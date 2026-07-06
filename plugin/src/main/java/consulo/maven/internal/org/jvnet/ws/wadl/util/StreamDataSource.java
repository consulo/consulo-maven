/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 * 
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.php
 * See the License for the specific language governing
 * permissions and limitations under the License.
 */
package consulo.maven.internal.org.jvnet.ws.wadl.util;

import jakarta.activation.DataSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author mh124079
 * @since 2007-04-18
 */
public class StreamDataSource implements DataSource {
    String mediaType;
    InputStream in;
    
    /** Creates a new instance of StreamDataSource */
    public StreamDataSource(String mediaType, InputStream in) {
        this.mediaType = mediaType;
        this.in = in;
    }

    @Override
    public String getContentType() {
        return mediaType;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return in;
    }

    @Override
    public String getName() {
        return "stream";
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return null;
    }
}
