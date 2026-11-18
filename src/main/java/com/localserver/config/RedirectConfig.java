package com.localserver.config;

import com.localserver.constants.Constants;

public class RedirectConfig {

    public String url;
    public int status = Constants.Http.DEFAULT_REDIRECT_STATUS;

    public RedirectConfig() {
    }

    public RedirectConfig(String url, int status) {
        this.url = url;
        this.status = status;
    }
}
