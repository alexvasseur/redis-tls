package org.example.redistls;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.util.Date;

/**
 *
 * 1 - Create a Redis Cloud instance with TLS enabled
 * 2 - Download the instance certificate and unzip it (redis_ca.pem)
 * 3 - Create a local truststore
 * keytool -import -noprompt -file redis_ca.pem -alias ca_cert -keystore ca.jks -storepass password
 * 4 - run with the SSL truststore system properties - see in the code below
 *
 * For mTLS you need also a keystore to store the Redis client public certificate and private key
 * openssl pkcs12 -export -in redis_user.crt -inkey redis_user_private.key -out client-keystore.p12 -passout pass:password
 *
 *
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Starting...");

        // default values
        String HOST = "redis-15161.c284.us-east1-2.gce.cloud.redislabs.com";
        int PORT = 15161;
        String AUTH = "password";
        boolean mTLS = false;

        // parse CLI
        String param = "";
        for (String s : args) {
            if ("-h".equals(param)) HOST = s;
            if ("-p".equals(param)) PORT = Integer.parseInt(s);
            if ("-a".equals(param)) AUTH = s;
            if ("--mtls".equals(s)) mTLS = true;
            param = s;
        }

        System.out.println("Connecting to:");
        System.out.println(" -h " + HOST);
        System.out.println(" -p " + PORT);
        System.out.println(" -u " + AUTH);
        if (mTLS)
            System.out.println(" --mtls");

        // custom local keystore where Redis Cloud custom CA is stored
        System.setProperty("javax.net.ssl.trustStoreType", "jks");
        System.setProperty("javax.net.ssl.trustStore", "ca.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "password");
        System.setProperty("javax.net.debug", "ssl");

        // for mTLS
        if (mTLS) {
            System.setProperty("javax.net.ssl.keyStore", "client-keystore.p12");
            System.setProperty("javax.net.ssl.keyStorePassword", "password");
        }

        // skip CA verification - carefully assess for a better implementation than this
        // see https://github.com/redis/jedis/blob/6b5025faa2c32b818ee894a20245e21c38747674/src/test/java/redis/clients/jedis/SSLJedisTest.java#L145
        HostnameVerifier allHostsValid = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };

        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        JedisPool jedisPool = new JedisPool(
                jedisPoolConfig,
                HOST,
                PORT,
                2000,
                AUTH,
                true,//TLS
                null,//SSLFactory
                null,//SSLparams
                null//allHostsValid //TODO not too sure why this is not mandatory to have one
        );

        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            jedis.set("redis-tls:test", new Date(System.currentTimeMillis()).toString());
            System.out.println("OK wrote to Redis");
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            if (jedis!=null) jedis.close();
            else System.err.println("FAIL to get Jedis connection");
        }
        System.out.println("DONE");
    }
}