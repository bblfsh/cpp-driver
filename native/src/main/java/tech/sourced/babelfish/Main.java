package tech.sourced.babelfish;

import java.io.*;
import java.util.Arrays;
import java.util.Scanner;

public class Main {

    enum ProcessCycle {CONTINUE, STOP}

    public static void main(String args[]) {
        while (true) {
            if (process() == ProcessCycle.STOP) {
                return; // stdin closed or unwrittable
            }
        }
    }

    //Try to send and error trough the response driver, print to stdout and stop and print on stderr if that fails
    private static ProcessCycle trySendError(String msg, Exception e) {
        try {
            final TranslationUnitJSONMapper responseJSONMapper =
                    new TranslationUnitJSONMapper(false, System.out);
            DriverResponse response = new DriverResponse(responseJSONMapper);
            response.sendError(e, msg);
            return ProcessCycle.CONTINUE;
        } catch (Exception j) {
            System.err.println(e.getMessage());
            System.err.println("BAILING OUT, CANT WRITE ERRORS");
            System.err.println("ADITTIONAL ERROR WHILE SENDING ERROR BELOW!");
            System.err.println(j.getMessage());
            return ProcessCycle.STOP;
        }
    }

    static private ProcessCycle process() {
        DriverResponse response = null;
        try {
            final EclipseCPPParser parser = new EclipseCPPParser();
            final TranslationUnitJSONMapper responseJSONMapper =
                new TranslationUnitJSONMapper(false, System.out);
            response = new DriverResponse(responseJSONMapper);

            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            final String inStr = in.readLine();
            if (inStr == null) {
                // stdin closed
                return ProcessCycle.STOP;
            }

            String requestContent = DriverRequest.load(inStr).content;
            response.parseCode(parser, requestContent);
            response.send();
            return ProcessCycle.CONTINUE;

        } catch (DriverRequest.RequestLoadException e) {
            return trySendError("Error reading the petition: ", e);
        } catch (DriverResponse.ResponseSendException e) {
            return trySendError("Error serializing the AST to JSON: ", e);
        } catch (IOException e) {
            return trySendError("A problem occurred while processing the petition: ", e);
        }
    }
}
