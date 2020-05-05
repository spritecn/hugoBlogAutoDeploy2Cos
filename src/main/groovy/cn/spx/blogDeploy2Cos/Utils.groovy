package cn.spx.blogDeploy2Cos

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.InvalidKeyException

class Utils {
    static String hmac(String scert,String postBody){
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(scert.getBytes(), "HmacSHA1")
            Mac mac = Mac.getInstance("HmacSHA1")
            mac.init(secretKeySpec)
            byte[] digest = mac.doFinal(postBody.getBytes())
            return digest.encodeHex().toString()
        } catch (InvalidKeyException e) {
            throw new RuntimeException("Invalid key exception while converting to HmacSHA1",e)
        }
    }

    public static void main(String[] args) {
        println hmac("aaa",'bbb')
    }
}
