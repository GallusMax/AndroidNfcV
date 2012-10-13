package org.android.nfc.tech;

public class Util {
    public static String toHex(byte[] in){
        String text=String.format("0x");
        for (byte  element : in) {
			text=text.concat(String.format("%02x", element));
		}
        return text;
    }
    
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

}
