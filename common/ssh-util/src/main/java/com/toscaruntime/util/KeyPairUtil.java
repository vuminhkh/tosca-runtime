package com.toscaruntime.util;

import java.io.FileReader;
import java.security.KeyPair;
import java.security.Security;

import org.apache.commons.lang.StringUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

public class KeyPairUtil {

    public static KeyPair loadKeyPair(String pemFile) {
        if (StringUtils.isBlank(pemFile)) {
            return null;
        }
        try {
            Security.addProvider(new BouncyCastleProvider());
            PEMParser pemParser = new PEMParser(new FileReader(pemFile));
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            Object object = pemParser.readObject();
            return converter.getKeyPair((PEMKeyPair) object);
        } catch (Exception e) {
            throw new RuntimeException("Could not load key pair", e);
        }
    }
}
