/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.coinblesk.server.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Service utility class used in this package.
 *
 * @author Thomas Bocek
 */
class ServiceUtils {

    private final static String USER_AGENT = "Mozilla/5.0";

    /**
     * Executes JSON HTTP Request and returns result.
     *
     * @param url
     * @return response of defined by url request
     * @throws IOException
     */
    public static StringBuffer doHttpRequest(String url) throws IOException {
        final URL requestURL = new URL(url);
        final HttpURLConnection con = (HttpURLConnection) requestURL.openConnection();

        // optional default is GET
        con.setRequestMethod("GET");

        // add request header
        con.setRequestProperty("User-Agent", USER_AGENT);

        final StringBuffer response = new StringBuffer();
        try (final BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
        }
        return response;
    }
}
