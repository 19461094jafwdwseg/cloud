import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;

public class CloudServer {

    private static final int PORT = 8080;

    private static final Path CONFIG_DIR =
            Paths.get("configs");

    public static void main(String[] args) throws Exception {

        Files.createDirectories(CONFIG_DIR);

        HttpServer server =
                HttpServer.create(
                        new InetSocketAddress(PORT),
                        0
                );

        server.createContext(
                "/api/configs",
                CloudServer::handleConfigs
        );

        server.setExecutor(
                Executors.newCachedThreadPool()
        );

        server.start();

        System.out.println(
                "Cloud API running on http://localhost:" + PORT
        );

        System.out.println(
                "Config directory: " +
                CONFIG_DIR.toAbsolutePath()
        );
    }


    private static void handleConfigs(
            HttpExchange exchange
    ) throws IOException {

        addCors(exchange);

        String method =
                exchange.getRequestMethod();

        String path =
                exchange.getRequestURI()
                        .getPath();

        try {

            if(path.equals("/api/configs"))
            {
                switch(method)
                {
                    case "GET":
                        getConfigs(exchange);
                        return;

                    case "POST":
                        createConfig(exchange);
                        return;

                    case "OPTIONS":
                        send(exchange, 204, "");
                        return;

                    default:
                        send(exchange, 405,
                                "{\"error\":\"Method not allowed\"}");
                        return;
                }
            }


            if(path.equals("/api/configs/upload"))
            {
                if(method.equals("POST"))
                {
                    uploadConfig(exchange);
                    return;
                }

                if(method.equals("OPTIONS"))
                {
                    send(exchange, 204, "");
                    return;
                }
            }


            if(path.startsWith("/api/configs/"))
            {
                String id =
                        path.substring(
                                "/api/configs/".length()
                        );

                id = URLDecoder.decode(
                        id,
                        StandardCharsets.UTF_8
                );

                switch(method)
                {
                    case "PUT":
                        updateConfig(exchange, id);
                        return;

                    case "DELETE":
                        deleteConfig(exchange, id);
                        return;

                    case "OPTIONS":
                        send(exchange, 204, "");
                        return;

                    default:
                        send(exchange, 405,
                                "{\"error\":\"Method not allowed\"}");
                        return;
                }
            }


            send(
                    exchange,
                    404,
                    "{\"error\":\"Not found\"}"
            );

        }
        catch(Exception e)
        {
            e.printStackTrace();

            send(
                    exchange,
                    500,
                    "{\"error\":\"Internal server error\"}"
            );
        }
    }


    /*
     * GET /api/configs
     */

    private static void getConfigs(
            HttpExchange exchange
    ) throws IOException {

        List<String> configs =
                new ArrayList<>();

        try(DirectoryStream<Path> stream =
                Files.newDirectoryStream(
                        CONFIG_DIR,
                        "*.cfg"
                ))
        {
            for(Path file : stream)
            {
                String data =
                        Files.readString(
                                file
                        );

                String id =
                        file.getFileName()
                                .toString()
                                .replace(".cfg", "");

                String name =
                        id;

                String environment =
                        "Production";

                configs.add(
                        "{"
                        + "\"id\":\""
                        + jsonEscape(id)
                        + "\","
                        + "\"name\":\""
                        + jsonEscape(name)
                        + "\","
                        + "\"environment\":\""
                        + environment
                        + "\","
                        + "\"data\":\""
                        + jsonEscape(data)
                        + "\""
                        + "}"
                );
            }
        }

        String response =
                "[" +
                String.join(",", configs) +
                "]";

        send(
                exchange,
                200,
                response
        );
    }


    /*
     * POST /api/configs
     */

    private static void createConfig(
            HttpExchange exchange
    ) throws IOException {

        String body =
                readBody(exchange);

        String name =
                jsonValue(body, "name");

        String environment =
                jsonValue(body, "environment");

        String data =
                jsonValue(body, "data");

        if(name == null || name.isBlank())
        {
            send(
                    exchange,
                    400,
                    "{\"error\":\"Name is required\"}"
            );

            return;
        }

        String id =
                UUID.randomUUID()
                        .toString();

        String safeName =
                safeFileName(name);

        Path file =
                CONFIG_DIR.resolve(
                        id + ".cfg"
                );

        String content =
                "# Name: " + safeName + "\n"
                + "# Environment: "
                + (environment == null
                    ? "Production"
                    : environment)
                + "\n\n"
                + (data == null ? "" : data);

        Files.writeString(
                file,
                content
        );

        send(
                exchange,
                201,
                "{"
                + "\"id\":\"" + id + "\","
                + "\"name\":\""
                + jsonEscape(name)
                + "\","
                + "\"environment\":\""
                + jsonEscape(
                    environment == null
                    ? "Production"
                    : environment
                )
                + "\","
                + "\"data\":\""
                + jsonEscape(
                    data == null ? "" : data
                )
                + "\""
                + "}"
        );
    }


    /*
     * PUT /api/configs/{id}
     */

    private static void updateConfig(
            HttpExchange exchange,
            String id
    ) throws IOException {

        Path file =
                CONFIG_DIR.resolve(
                        safeFileName(id) + ".cfg"
                );

        if(!Files.exists(file))
        {
            send(
                    exchange,
                    404,
                    "{\"error\":\"Config not found\"}"
            );

            return;
        }

        String body =
                readBody(exchange);

        String name =
                jsonValue(body, "name");

        String environment =
                jsonValue(body, "environment");

        String data =
                jsonValue(body, "data");

        String content =
                "# Name: "
                + safeFileName(
                    name == null ? id : name
                )
                + "\n"
                + "# Environment: "
                + (
                    environment == null
                    ? "Production"
                    : environment
                )
                + "\n\n"
                + (
                    data == null
                    ? ""
                    : data
                );

        Files.writeString(
                file,
                content
        );

        send(
                exchange,
                200,
                "{\"success\":true}"
        );
    }


    /*
     * DELETE /api/configs/{id}
     */

    private static void deleteConfig(
            HttpExchange exchange,
            String id
    ) throws IOException {

        Path file =
                CONFIG_DIR.resolve(
                        safeFileName(id) + ".cfg"
                );

        if(!Files.exists(file))
        {
            send(
                    exchange,
                    404,
                    "{\"error\":\"Config not found\"}"
            );

            return;
        }

        Files.delete(file);

        send(
                exchange,
                200,
                "{\"success\":true}"
        );
    }


    /*
     * POST /api/configs/upload
     *
     * Simple multipart upload handler.
     */

    private static void uploadConfig(
            HttpExchange exchange
    ) throws IOException {

        String contentType =
                exchange.getRequestHeaders()
                        .getFirst("Content-Type");

        if(contentType == null ||
           !contentType.startsWith(
                "multipart/form-data"))
        {
            send(
                    exchange,
                    400,
                    "{\"error\":\"Expected multipart upload\"}"
            );

            return;
        }

        String boundary =
                getBoundary(contentType);

        if(boundary == null)
        {
            send(
                    exchange,
                    400,
                    "{\"error\":\"Missing boundary\"}"
            );

            return;
        }

        byte[] body =
                exchange.getRequestBody()
                        .readAllBytes();

        String raw =
                new String(
                        body,
                        StandardCharsets.ISO_8859_1
                );

        String marker =
                "filename=\"";

        int filenameStart =
                raw.indexOf(marker);

        if(filenameStart < 0)
        {
            send(
                    exchange,
                    400,
                    "{\"error\":\"No file supplied\"}"
            );

            return;
        }

        filenameStart +=
                marker.length();

        int filenameEnd =
                raw.indexOf(
                        "\"",
                        filenameStart
                );

        String filename =
                raw.substring(
                        filenameStart,
                        filenameEnd
                );

        filename =
                safeFileName(filename);

        int dataStart =
                raw.indexOf(
                        "\r\n\r\n",
                        filenameEnd
                );

        if(dataStart < 0)
        {
            send(
                    exchange,
                    400,
                    "{\"error\":\"Invalid upload\"}"
            );

            return;
        }

        dataStart += 4;

        String endMarker =
                "\r\n--" + boundary;

        int dataEnd =
                raw.indexOf(
                        endMarker,
                        dataStart
                );

        if(dataEnd < 0)
        {
            send(
                    exchange,
                    400,
                    "{\"error\":\"Invalid upload data\"}"
            );

            return;
        }

        String fileData =
                raw.substring(
                        dataStart,
                        dataEnd
                );

        String id =
                UUID.randomUUID()
                        .toString();

        Path file =
                CONFIG_DIR.resolve(
                        id + "-" + filename
                );

        Files.writeString(
                file,
                fileData,
                StandardCharsets.UTF_8
        );

        send(
                exchange,
                201,
                "{"
                + "\"success\":true,"
                + "\"id\":\""
                + id
                + "\""
                + "}"
        );
    }


    private static String getBoundary(
            String contentType
    ) {

        String search =
                "boundary=";

        int index =
                contentType.indexOf(search);

        if(index < 0)
            return null;

        return contentType
                .substring(
                        index + search.length()
                )
                .trim()
                .replace("\"", "");
    }


    private static String readBody(
            HttpExchange exchange
    ) throws IOException {

        return new String(
                exchange
                    .getRequestBody()
                    .readAllBytes(),
                StandardCharsets.UTF_8
        );
    }


    /*
     * Very small JSON helper for this prototype.
     * For production, use Jackson/Gson.
     */

    private static String jsonValue(
            String json,
            String key
    ) {

        String search =
                "\"" + key + "\"";

        int keyIndex =
                json.indexOf(search);

        if(keyIndex < 0)
            return null;

        int colon =
                json.indexOf(
                        ":",
                        keyIndex
                );

        if(colon < 0)
            return null;

        int start =
                json.indexOf(
                        "\"",
                        colon
                );

        if(start < 0)
            return null;

        start++;

        StringBuilder result =
                new StringBuilder();

        boolean escaped = false;

        for(int i = start;
            i < json.length();
            i++)
        {
            char c =
                    json.charAt(i);

            if(escaped)
            {
                result.append(c);
                escaped = false;
                continue;
            }

            if(c == '\\')
            {
                escaped = true;
                continue;
            }

            if(c == '"')
                break;

            result.append(c);
        }

        return result.toString();
    }


    private static String safeFileName(
            String name
    ) {

        if(name == null || name.isBlank())
            return "config";

        return name
                .replaceAll(
                        "[^a-zA-Z0-9._-]",
                        "_"
                );
    }


    private static String jsonEscape(
            String value
    ) {

        if(value == null)
            return "";

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }


    private static void addCors(
            HttpExchange exchange
    ) {

        Headers headers =
                exchange.getResponseHeaders();

        headers.set(
                "Access-Control-Allow-Origin",
                "*"
        );

        headers.set(
                "Access-Control-Allow-Methods",
                "GET,POST,PUT,DELETE,OPTIONS"
        );

        headers.set(
                "Access-Control-Allow-Headers",
                "Content-Type"
        );
    }


    private static void send(
            HttpExchange exchange,
            int status,
            String response
    ) throws IOException {

        byte[] bytes =
                response.getBytes(
                        StandardCharsets.UTF_8
                );

        exchange.getResponseHeaders()
                .set(
                    "Content-Type",
                    "application/json"
                );

        exchange.sendResponseHeaders(
                status,
                bytes.length
        );

        try(OutputStream output =
                exchange.getResponseBody())
        {
            output.write(bytes);
        }
    }
}