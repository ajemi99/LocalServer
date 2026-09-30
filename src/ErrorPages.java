public class ErrorPages {

    public static HttpResponse get(int code) {
        String message = switch (code) {
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 413 -> "Payload Too Large";
            default  -> { code = 500; yield "Internal Server Error"; }
        };

        String html = "<!DOCTYPE html>\n"
                + "<html><head><meta charset=\"UTF-8\"><title>" + code + " " + message + "</title></head>\n"
                + "<body style=\"font-family:sans-serif;text-align:center;margin-top:15%\">\n"
                + "<h1>" + code + "</h1>\n"
                + "<p>" + message + "</p>\n"
                + "<hr><small>LocalServer</small>\n"
                + "</body></html>\n";

        return new HttpResponse(code + " " + message, "text/html; charset=UTF-8", html);
    }
}