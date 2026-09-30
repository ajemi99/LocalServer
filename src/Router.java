
public class Router {



    public static HttpResponse route(HttpRequest request) {

        String body;
        if (request.path.equals("/")) {
            body = "Hello from LocalServer";
        } else if (request.path.equals("/hello")) {
            body = "Hello!";
        } else {
            // path ma kaynch
           return ErrorPages.get(404);
        }

        // path kayn, nchofo method
        if (!request.method.equals("GET")) {
            HttpResponse r = ErrorPages.get(405);
            r.extraHeaders = "Allow: GET\r\n";
            return r;
        }

        return new HttpResponse("200 OK", "text/plain", body);
    }
}
