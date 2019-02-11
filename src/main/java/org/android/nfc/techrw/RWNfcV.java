/**
  	$Id: $
  	Copyright (C) 2011 Ulrich Hahn
  	
  	This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

 */
package org.android.nfc.techrw;

import java.io.IOException;

import org.android.nfc.tech.ReadNfcV;
import org.android.nfc.tech.Util;

import android.nfc.Tag;
import android.util.Log;

/**
 * @author uhahn
 * encapsulation of modifying RFD operations
 */
public class RWNfcV extends ReadNfcV {

	private static final String TAG = RWNfcV.class.getSimpleName();

	/**
	 * @param t
	 */
	public RWNfcV(Tag t) {
		super(t);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param sysinfo
	 */
	public RWNfcV(String sysinfo) {
		super(sysinfo);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param sysinfo
	 * @param userdata
	 */
	public RWNfcV(String sysinfo, String userdata) {
		super(sysinfo, userdata);
		// TODO Auto-generated constructor stub
	}

	public byte[] writeAFI(byte newAFI) throws IOException{
		return setAFI(newAFI);
	}
	public byte[] setAFI(byte newAFI) throws IOException{
		byte[] res=transceive((byte)0x27, ((int)newAFI)&0xff);
		// TODO what if res shows an error?
		Log.d(TAG,"setAFI returns "+Util.toHex(res));
		return res;
	}

	/**
	 * write one block
	 * @param i - the block number
	 * @param data - the bytes to be written
	 * @return the transceive result
	 * @throws IOException 
	 */
	public byte[] writeSingleBlock(int i,byte[] data) throws IOException {
		return transceive((byte)0x21,i,-1,data); 
	}

	/**
	 * write several blocks 
	 * @param i index of starting block
	 * @param n number of blocks to be written
	 * @param data byte array containing the data to be written
	 * @return the error code
	 * @throws IOException 
	 */
	public byte[] writeMultipleBlocks(int i,int n,byte[] data) throws IOException{
		if((n*blocksize) > data.length){
			Log.e(TAG,"data array shorter than blocks to be written");
			return new byte[]{-1};  // TODO return an error code here
		}
		return transceive((byte)0x24,i,n-1,data); 
	}


	
}
