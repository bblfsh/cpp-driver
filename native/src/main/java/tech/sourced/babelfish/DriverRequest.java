package tech.sourced.babelfish;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;

/**
 * Class for the C/C++ driver request.
 */

// To ignore any unknown properties in JSON input without exception
@JsonIgnoreProperties(ignoreUnknown = true)
class DriverRequest {
    static class RequestLoadException extends IOException {
        RequestLoadException(Throwable e) {
            super(e);
        }
    }

    public String action;
    public String language;
    public String languageVersion;
    public String content;
    public String encoding;

    public DriverRequest() {} // Dummy constructor, jackson needs this
    static DriverRequest load(String in) throws RequestLoadException {
        // NOTE: If we add new protocols this need to be decoupled from jackson through an
        // intermediate interface (IExchangeFormatReader) like DriverResponse is, but for now with a single protocol is overkill
        // to add more layers
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(in, DriverRequest.class);
        } catch (IOException e) {
            throw new DriverRequest.RequestLoadException(e);
        }
    }
}
