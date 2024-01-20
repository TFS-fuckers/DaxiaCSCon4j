package com.tfs.dxcscon4j;

public class DXSys {
    protected static VertificationStrategy vertificationStrategy = (v) -> true;
    
    public static void setVertificationStrategy(VertificationStrategy v) {
        DXSys.vertificationStrategy = v;
    }

    protected static class Logging {
        private Logging() {}

        private static MessageRedirector logger = (message, head) -> {
            System.out.printf("[%s] %s\n", head, message);
        };

        public static void logInfo(String message) {
            logger.log(message, "INFO");
        }

        public static void logInfo(String format, Object... args) {
            logInfo(String.format(format, args));
        }
    
        public static void logWarning(String message) {
            logger.log(message, "WARNING");
        }    

        public static void logWarning(String format, Object... args) {
            logWarning(String.format(format, args));
        }

        public static void logError(String message) {
            logger.log(message, "ERROR");
        }

        public static void logError(String format, Object... args) {
            logError(String.format(format, args));
        }

        public static void setLogger(MessageRedirector logger) {
            Logging.logger = logger;
        }
    }
}