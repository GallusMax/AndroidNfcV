package org.android.nfc.tech;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import android.os.Environment;
import android.util.Log;

public class Util {
    private static final String TAG = "Util";

    /**
     * format (lowercase) hex from a byte[]
     * @param in - a byte array 
     * @return - a hex string
     */
	public static String toHex(byte[] in){
        String text="";//String.format("0x");
        if(null==in)return text; // dont puke on null
        for (byte  element : in) {
			text=text.concat(String.format("%02x", element));
		}
        return text;
    }
    
    public static byte[] hexStringToByteArray(String s) {
    	//TODO what if null==s?
    	int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
    
    
    public static void LogTag(String buffer) {
        // pictures and other media owned by the application, consider
        // Context.getExternalMediaDir().
    	
        File path = Environment.getExternalStoragePublicDirectory(
       		Environment.DIRECTORY_DOWNLOADS);
        
        String fname = "alltagssofar.log";
        boolean append=true;
        
        File file = new File(path, fname);

        try {
           FileWriter os=null;
//           os = openFileOutput(path+"/"+fname, MODE_APPEND); // under /data/data/APPNAME/files - 
           os=new FileWriter(file, append);
           os.write(buffer);
           os.write("\n");
           os.close();
        }
        catch(IOException e){
        	Log.e(TAG, "LogTag fails "+file,e);
        }
    }

}
