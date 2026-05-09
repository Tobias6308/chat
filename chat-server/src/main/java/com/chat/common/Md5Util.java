package com.chat.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Md5Util {
    
    public static String encrypt(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 加密失败", e);
        }
    }
    
    public static boolean verify(String input, String md5Hash) {
        if (input == null || md5Hash == null) {
            System.out.println("Md5Util.verify - input or hash is null, input=" + input + ", hash=" + md5Hash);
            return false;
        }
        String inputHash = encrypt(input);
        boolean result = inputHash.equals(md5Hash);
        System.out.println("Md5Util.verify - inputHash=" + inputHash + ", storedHash=" + md5Hash + ", result=" + result);
        return result;
    }
}