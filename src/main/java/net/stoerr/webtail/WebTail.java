/*
 * Copyright (c) 2013 T-Systems Multimedia Solutions GmbH Dresden
 * Riesaer Str. 7, D-01129 Dresden, Germany
 * All rights reserved.
 *
 * WebTail.java created by hps at 29.05.2013
 * WebTail.java updated by Heitor Carneiro at 29.06.2020
 */
package net.stoerr.webtail;

import com.wuriyanto.jvmstash.Stash;
import com.wuriyanto.jvmstash.StashException;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Simplest possible implementation of tail -F for a file on the web : retrieves endlessly the lines that are new in a URL.
 * Arguments: url proxyhost proxyport ; arguments proxyhost and proxyport are optional.
 * Done since http://www.jibble.org/webtail/ failed with CIT2 for some unknown reason.
 *
 * @author hps
 * @since 13.3 , 29.05.2013
 * @author Heitor Carneiro
 * @since 14 , 29.06.2020
 */
public class WebTail {

    private static final Logger LOGGER = Logger.getLogger(Stash.class.getName());
    private static final int SLEEP_TIME = 5000;
    public static final int MIN_MESSAGE_LENGTH = 4;

    private final String url;
    private final String logstashHost;
    private final Integer logstashPort;
    private final HttpClient httpClient = new DefaultHttpClient();
    private long lastread = Long.MAX_VALUE;
    private final byte[] buf = new byte[65536];
    private final Stash stash;

    public static void main(String[] args) throws Exception {
        new WebTail(args).run();
    }

    public WebTail(String[] args) throws StashException {
        List<String> validArgs = new ArrayList<>();
        for (String arg : args) {
            if (arg != null && !arg.trim().equals("")) {
                validArgs.add(arg);
            }
        }

        int size = validArgs.size();
        if (size < 3) {
            throw new IllegalArgumentException("Arguments 'URL', 'LOGSTASH_HOST', 'LOGSTASH_PORT' are mandatory.");
        }

        url = validArgs.get(0);
        logstashHost = validArgs.get(1);
        logstashPort = Integer.valueOf(validArgs.get(2));
        if (size == 5) {
            final HttpHost proxy = new HttpHost(validArgs.get(3), Integer.parseInt(validArgs.get(4)));
            httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
        }

        this.stash = new Stash.Builder()
                .setHost(logstashHost)
                .setPort(logstashPort)
                .setSecure(false)
                .build();
    }

    private void run() throws Exception {
        LOGGER.info("Tailing URL " + url + " starting from " + retrievesize() + " with logstash " + logstashHost + ":" + logstashPort);
        while (true) {
            printNewLoglines();
            Thread.sleep(SLEEP_TIME);
        }
    }

    private void printNewLoglines() throws IOException {
        long currentsize = retrievesize();
        if (currentsize <= lastread)
            return;
        HttpGet get = new HttpGet(url);
        get.addHeader("Range", "bytes=" + lastread + "-" + currentsize);
        HttpResponse response = httpClient.execute(get);
        if (HttpStatus.SC_PARTIAL_CONTENT != response.getStatusLine().getStatusCode())
            throw new IllegalStateException("Unexpected status " + response.getStatusLine() + " in " + response);
        writeReceivedContentpart(response);
        lastread = currentsize;
    }

    private void writeReceivedContentpart(HttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        InputStream stream = entity.getContent();

        StringBuilder sb = new StringBuilder();
        for (int read; (read = stream.read(buf)) > 0;) {
            String buffer = new String(buf, 0, read);
            sb.append(buffer);
        }

        String lineSeparator = "\n";
        String[] lines = sb
                .toString()
                .replaceAll("\\r\\n", lineSeparator)
                .replaceAll("\\r", lineSeparator)
                .split(lineSeparator);

        try {
            stash.connect();
            for (String line : lines) {
                if (null == line)
                    continue;

                String sanitizedLine = line.trim();
                if (MIN_MESSAGE_LENGTH > sanitizedLine.length())
                    continue;

                stash.write(line.getBytes());
            }
            stash.close();
        } catch (StashException e) {
            LOGGER.severe("Fail to connect in logstash: " + e.getMessage());
        }
    }

    private long retrievesize() throws IOException {
        HttpHead head = new HttpHead(url);
        HttpResponse response = httpClient.execute(head);
        if (null == response)
            return lastread;

        Header acceptRanges = response.getFirstHeader("Accept-Ranges");
        if (null == acceptRanges || !acceptRanges.getValue().contains("bytes"))
            throw new IllegalStateException("Ranges not supported in " + response);

        return getContentLength(response);
    }

    private long getContentLength(HttpResponse response) {
        Header contentLength = response.getFirstHeader("Content-Length");
        if (null == contentLength || null == contentLength.getValue())
            return lastread;

        long contentLengthValue = Long.parseLong(contentLength.getValue());
        if (contentLengthValue < lastread) {
            lastread = contentLengthValue;
        }
        return contentLengthValue;
    }
}
