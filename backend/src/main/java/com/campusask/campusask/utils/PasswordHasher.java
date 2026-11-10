package com.campusask.campusask.utils;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Versioned, salted PBKDF2 hashes fit the existing VARCHAR(100) password column. */
public final class PasswordHasher {
    private static final int ITERATIONS=600_000;
    private static final SecureRandom RANDOM=new SecureRandom();
    private PasswordHasher() {}
    public static void validateNew(String raw) {
        if(raw==null || raw.length()<8 || raw.length()>128 || raw.isBlank()) throw new IllegalArgumentException("新密码需为 8～128 个字符");
    }
    public static String hash(String raw) {
        if(raw==null || raw.length()>128) throw new IllegalArgumentException("密码格式无效");
        byte[] salt=new byte[16];RANDOM.nextBytes(salt);
        return "pbkdf2$"+ITERATIONS+"$"+Base64.getEncoder().encodeToString(salt)+"$"+Base64.getEncoder().encodeToString(derive(raw,salt,ITERATIONS));
    }
    public static boolean legacy(String encoded) {return encoded!=null && encoded.matches("[a-fA-F0-9]{32}");}
    public static boolean matches(String raw,String encoded) {
        if(raw==null || raw.length()>128 || encoded==null) return false;
        try {
            if(legacy(encoded)) {
                byte[] expected=HexFormat.of().parseHex(encoded);
                return MessageDigest.isEqual(expected,MessageDigest.getInstance("MD5").digest(raw.getBytes(StandardCharsets.UTF_8)));
            }
            String[] parts=encoded.split("\\$");
            if(parts.length!=4 || !parts[0].equals("pbkdf2")) return false;
            int iterations=Integer.parseInt(parts[1]);if(iterations<100_000 || iterations>ITERATIONS) return false;
            byte[] salt=Base64.getDecoder().decode(parts[2]), expected=Base64.getDecoder().decode(parts[3]);
            if(salt.length!=16 || expected.length!=32) return false;
            return MessageDigest.isEqual(expected,derive(raw,salt,iterations));
        } catch(Exception ignored) {return false;}
    }
    private static byte[] derive(String raw,byte[] salt,int iterations) {
        PBEKeySpec spec=new PBEKeySpec(raw.toCharArray(),salt,iterations,256);
        try {return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
        catch(GeneralSecurityException e) {throw new IllegalStateException("密码加密服务不可用",e);}
        finally {spec.clearPassword();}
    }
}
