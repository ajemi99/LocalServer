package com.localserver.handlers;

import com.localserver.config.ServerConfig;
import com.localserver.http.ErrorPages;
import com.localserver.http.HttpRequest;
import com.localserver.http.HttpResponse;
import com.localserver.constants.Constants;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

/**
 * Executes CGI scripts with {@link ProcessBuilder} and converts their output to
 * an HTTP response. The process working directory is the configured base
 * directory so scripts using documented relative paths do not depend on where
 * the JVM was launched.
 */
public final class CGIHandler {

    private CGIHandler() {
    }

    public static HttpResponse execute(HttpRequest request, Path scriptPath, String interpreter, ServerConfig server) {
        try {
            ProcessBuilder pb = new ProcessBuilder(interpreter, scriptPath.toAbsolutePath().toString());
            pb.directory(server.baseDir.toFile());

            // 1. CGI environment variables
            Map<String, String> env = pb.environment();
            env.put(Constants.Cgi.ENV_REQUEST_METHOD, request.method);
            env.put(Constants.Cgi.ENV_SERVER_PROTOCOL, request.version);
            env.put(Constants.Cgi.ENV_REDIRECT_STATUS, Constants.Cgi.REDIRECT_STATUS_VALUE); // Required by some PHP/Python scripts

            int q = request.path.indexOf('?');
            if (q != -1) {
                env.put("QUERY_STRING", request.path.substring(q + 1));
            } else {
                env.put("QUERY_STRING", "");
            }

            if (request.headers.containsKey(Constants.Http.HEADER_CONTENT_LENGTH)) {
                env.put(Constants.Cgi.ENV_CONTENT_LENGTH, request.headers.get(Constants.Http.HEADER_CONTENT_LENGTH));
            }
            if (request.headers.containsKey(Constants.Http.HEADER_CONTENT_TYPE)) {
                env.put(Constants.Cgi.ENV_CONTENT_TYPE, request.headers.get(Constants.Http.HEADER_CONTENT_TYPE));
            }

            Process process = pb.start();

            // 2. For POST, send the body to the script on stdin
            try (OutputStream os = process.getOutputStream()) {
                if (Constants.Http.METHOD_POST.equalsIgnoreCase(request.method) && request.body != null && request.body.length > 0) {
                    os.write(request.body);
                    os.flush();
                }
            }

            // 3. Read the script result (stdout)
            byte[] cgiOutput;
            try (InputStream is = process.getInputStream()) {
                cgiOutput = is.readAllBytes();
            }
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                return ErrorPages.get(500);
            }

            // 4. Separate CGI headers from the response body
            String rawOutput = new String(cgiOutput, Constants.Http.UTF_8);
            int headerEnd = rawOutput.indexOf(Constants.Http.CRLF + Constants.Http.CRLF);
            int delimiterLength = 4;

            if (headerEnd == -1) {
                headerEnd = rawOutput.indexOf("\n\n");
                delimiterLength = 2;
            }

            if (headerEnd == -1) {
                // If the script does not generate headers, return plain text
                return new HttpResponse(Constants.Http.DEFAULT_STATUS, Constants.Http.CONTENT_TYPE_HTML, cgiOutput);
            }

            String headersPart = rawOutput.substring(0, headerEnd);
            byte[] bodyPart = rawOutput.substring(headerEnd + delimiterLength).getBytes(StandardCharsets.UTF_8);

            String contentType = Constants.Http.CONTENT_TYPE_HTML;
            HttpResponse response = new HttpResponse(Constants.Http.DEFAULT_STATUS, contentType, bodyPart);

            // Parse script headers (e.g. Content-Type)
            for (String line : headersPart.split("\r?\n")) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String name = line.substring(0, colon).trim();
                    String value = line.substring(colon + 1).trim();
                    if (name.equalsIgnoreCase(Constants.Http.HEADER_CONTENT_TYPE)) {
                        response.contentType = value;
                    } else if (name.equalsIgnoreCase("Status")) {
                        response.status = value;
                    } else {
                        response.extraHeaders += name + ": " + value + Constants.Http.CRLF;
                    }
                }
            }

            return response;

        } catch (Exception e) {
            e.printStackTrace();
            return ErrorPages.get(500);
        }
    }
}
