package tech.sourced.babelfish;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;


public class DriverResponse {
    static class ResponseSendException extends IOException {
        ResponseSendException(Throwable e) {
            super(e);
        }
    }

    public String driver = "1.0.0";
    public String language = "C++";
    public String languageVersion = "14";
    public String status = "ok";
    public ArrayList<String> errors = new ArrayList<String>(0);
    @JsonProperty("ast")
    private IASTTranslationUnit translationUnit;
    private IExchangeFormatWritter formatWritter;

    DriverResponse(IExchangeFormatWritter mapper) {
        this.formatWritter = mapper;
    }

    DriverResponse(String driver, String language, String languageVersion, IExchangeFormatWritter mapper) {
        this.driver = driver;
        this.language = language;
        this.languageVersion = languageVersion;
        this.formatWritter = mapper;
    }

    void parseCode(EclipseCPPParser parser, String source) {
        translationUnit = parser.parseCPP(source);
    }

    void send(OutputStream out) throws ResponseSendException {
        try {
            formatWritter.writeValue(this);
            ByteArrayOutputStream byteOut = formatWritter.getByteOutputStream();
            out.write(byteOut.toByteArray());
            byteOut.flush();
            byteOut.reset();
            out.flush();
        } catch (IOException e) {
            throw new DriverResponse.ResponseSendException(e);
        }
    }

    void sendError(BufferedOutputStream bufferOutputStream, Exception e, String errorString)
            throws IOException {

        translationUnit = null;
        errors.add(e.getClass().getCanonicalName());
        errors.add(errorString + e.getMessage());
        status = "fatal";
        send(bufferOutputStream);
    }
}
