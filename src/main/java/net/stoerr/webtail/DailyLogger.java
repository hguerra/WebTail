package net.stoerr.webtail;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class DailyLogger {

    public static final String LOGGER_NAME = "WebTail";
    public static final String LOGS_FOLDER = "logs";

    private static Logger logger;
    private static FileHandler fh;

    public static Logger getLogger() {
        if (logger == null) {
            initLogger();

            ScheduledThreadPoolExecutor sch = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1);
            Runnable renameLoggerFile = new Runnable() {
                @Override
                public void run() {
                    fh.flush();
                    fh.close();

                    try {
                        fh = createFilehandler(new java.sql.Date(System.currentTimeMillis()).toString());
                        SimpleFormatter formatter = new SimpleFormatter();

                        fh.setFormatter(formatter);
                        logger.addHandler(fh);
                        logger.warning("Runnable executed, new FileHandler is in use!");
                    } catch (SecurityException | IOException e) {
                        e.printStackTrace();
                    }
                }
            };

            Calendar c = Calendar.getInstance();
            long now = c.getTimeInMillis();
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            long passed = now - c.getTimeInMillis();
            long secondsPassedToday = passed / 1000;
            long secondsInDay = 86400;

            TimeUnit timeUnit = TimeUnit.SECONDS;
            long initialDelay = secondsInDay - secondsPassedToday;
            long relaunchPeriod = secondsInDay;
            sch.scheduleAtFixedRate(renameLoggerFile, initialDelay, relaunchPeriod, timeUnit);

        }
        return logger;
    }

    private static FileHandler createFilehandler(String dateForName) throws SecurityException, IOException {
        File fileFolder = new File(LOGS_FOLDER);
        // Create folder log if it doesn't exist
        if (!fileFolder.exists()) {
            fileFolder.mkdirs();
        }

        dateForName = LOGS_FOLDER + File.separator + dateForName + ".log";

        boolean appendToFile = true;

        return new FileHandler(dateForName, appendToFile);
    }

    private static void initLogger() {
        File fileFolder = new File(LOGS_FOLDER);
        // Create folder "log" if it doesn't exist
        if (!fileFolder.exists()) {
            fileFolder.mkdirs();
        }

        logger = Logger.getLogger(LOGGER_NAME);

        try {
            // This block configure the logger with handler and formatter
            boolean appendToFile = true;
            fh = new FileHandler(LOGS_FOLDER + File.separator + new java.sql.Date(System.currentTimeMillis()) + ".log", appendToFile);

            logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);

            // the following statement is used to log any messages
            logger.info("DailyLogger initialized...");
        } catch (SecurityException | IOException e) {
            logger.warning("Problem at initializing logger... " + e.getMessage());
        }
    }

}
