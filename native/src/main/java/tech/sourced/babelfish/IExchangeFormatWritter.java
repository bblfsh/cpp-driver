package tech.sourced.babelfish;

import java.io.OutputStream;
import java.io.IOException;

/**
 * This defines the interfaces for classes that implement AST serializers to some
 * specific format
 */
public interface IExchangeFormatWritter
{
    void writeValue(DriverResponse response) throws IOException;
    OutputStream getOutputStream();
}
