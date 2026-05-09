package com.chat.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

@Aspect
@Component
public class SlowRequestAspect {

    private static final Logger logger = LoggerFactory.getLogger(SlowRequestAspect.class);

    @Value("${logging.file.name:}")
    private String logBasePath;

    private static final long SLOW_THRESHOLD_MS = 1000;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Around("execution(* com.chat.controller..*.*(..))")
    public Object logSlowRequest(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        try {
            return joinPoint.proceed();
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            if (duration > SLOW_THRESHOLD_MS) {
                String method = joinPoint.getSignature().toShortString();
                Object[] args = joinPoint.getArgs();

                StringBuilder sb = new StringBuilder();
                sb.append("[").append(DATE_FORMAT.format(new Date())).append("] ");
                sb.append("[SLOW] ").append(method).append(" ");
                sb.append("duration=").append(duration).append("ms ");

                if (args != null && args.length > 0) {
                    sb.append("params=");
                    for (int i = 0; i < args.length; i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(truncate(String.valueOf(args[i]), 200));
                    }
                }

                String logMessage = sb.toString();
                logger.warn(logMessage);
                writeToSlowLogFile(logMessage);
            }
        }
    }

    private void writeToSlowLogFile(String message) {
        String logDir = System.getProperty("user.home") + File.separator + "logs" + File.separator + "chat-server";
        File dir = new File(logDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String fileName = "slow-request-" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".log";
        File logFile = new File(dir, fileName);

        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.println(message);
        } catch (IOException e) {
            logger.error("Failed to write slow request log: {}", e.getMessage());
        }
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return "null";
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }
}