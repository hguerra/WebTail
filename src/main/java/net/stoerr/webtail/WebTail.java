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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Simplest possible implementation of tail -F for a file on the web : retrieves endlessly the lines that are new in a URL.
 * Arguments: url proxyhost proxyport ; arguments proxyhost and proxyport are optional.
 * Done since http://www.jibble.org/webtail/ failed with CIT2 for some unknown reason.
 *
 * @author hps
 * @author Heitor Carneiro
 * @since 13.3 , 29.05.2013
 * @since 14 , 29.06.2020
 */
public class WebTail {

    private static final Logger LOGGER = DailyLogger.getLogger();
    private static final int SLEEP_TIME = 5000;
    private static final int MIN_MESSAGE_LENGTH = 4;

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
            LOGGER.severe("Arguments 'URL', 'LOGSTASH_HOST', 'LOGSTASH_PORT' are mandatory.");
            System.exit(1);
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

    private void run() {
        LOGGER.info("Tailing URL " + url + " starting from " + retrievesize() + " with logstash " + logstashHost + ":" + logstashPort);
        while (true) {
            printNewLoglines();
            sleep();
        }
    }

    private void sleep() {
        try {
            Thread.sleep(SLEEP_TIME);
        } catch (InterruptedException e) {
            LOGGER.severe("Fail in Thread.sleep: " + e.getMessage());
        }
    }

    private void printNewLoglines() {
        long currentsize = retrievesize();
        if (currentsize <= lastread)
            return;

        HttpGet get = new HttpGet(url);
        get.addHeader("Range", "bytes=" + lastread + "-" + currentsize);
        HttpResponse response = null;

        try {
            response = httpClient.execute(get);
        } catch (IOException e) {
            LOGGER.severe("Fail to execute request in printNewLoglines: " + e.getMessage());
        }

        if (null == response)
            return;

        if (HttpStatus.SC_PARTIAL_CONTENT != response.getStatusLine().getStatusCode())
            throw new IllegalStateException("Unexpected status " + response.getStatusLine() + " in " + response);

        writeReceivedContentpart(response);
        lastread = currentsize;
    }

    private void writeReceivedContentpart(HttpResponse response) {
        HttpEntity entity = response.getEntity();
        InputStream stream = null;

        try {
            stream = entity.getContent();
        } catch (IOException e) {
            LOGGER.severe("Fail to get response content in writeReceivedContentpart: " + e.getMessage());
        }

        if (null == stream)
            return;

        StringBuilder sb = new StringBuilder();

        try {
            for (int read; (read = stream.read(buf)) > 0; ) {
                String buffer = new String(buf, 0, read);
                sb.append(buffer);
            }
        } catch (IOException e) {
            LOGGER.severe("Fail to read buffer in writeReceivedContentpart: " + e.getMessage());
        }

        if (sb.toString().isBlank())
            return;

        String lineSeparator = "\n";
        String[] rawLines = sb
                .toString()
                .replaceAll("\\r\\n", lineSeparator)
                .replaceAll("\\r", lineSeparator)
                .split(lineSeparator);

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String date = formatter.format(now);

        String space = " ";
        sb = new StringBuilder();
        for (String line : rawLines) {
            if (null == line)
                continue;

            String sanitizedLine = line.trim();
            if (sanitizedLine.isBlank())
                continue;

            if (MIN_MESSAGE_LENGTH > sanitizedLine.length() || !sanitizedLine.startsWith(date)) {
                sb.append(space);
            } else {
                sb.append(lineSeparator);
            }

            sb.append(sanitizedLine);
        }

        String[] sanitizedLines = sb
                .toString()
                .split(lineSeparator);

        if (sanitizedLines.length == 0)
            return;

        try {
            stash.connect();
            for (String line : sanitizedLines) {
                if (line.isBlank())
                    continue;

                stash.write(line.getBytes());
            }
            stash.close();
        } catch (StashException | IOException e) {
            LOGGER.severe("Fail to connect in logstash: " + e.getMessage());
        }
    }

    private long retrievesize() {
        HttpHead head = new HttpHead(url);
        HttpResponse response = null;

        try {
            response = httpClient.execute(head);
        } catch (IOException e) {
            LOGGER.warning("Failt to execute request in retrievesize: " + e.getMessage());
        }

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
