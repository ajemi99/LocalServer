package com.localserver.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RouteConfig {

    public String path;                                   // ex: "/", "/uploads"
    public List<String> methods = new ArrayList<>();      // ex: ["GET", "POST"]
    public RedirectConfig redirect;   // ex: { "url": "/new-place", "status": 301 } (null = no redirection)
    public String root;                                   // directory/file on disk
    public String index;                                  // default file of a directory
    public Map<String, String> cgi = new LinkedHashMap<>(); // extension -> interpreter (".py" -> "python")
    public boolean directoryListing = false;              // list directory contents?
}
