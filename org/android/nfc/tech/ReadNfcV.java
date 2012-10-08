/*
 *  $Id: $
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
package org.android.nfc.tech;

import java.io.IOException;

import org.apache.http.util.ByteArrayBuffer;

import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.NfcV;
import android.nfc.tech.TagTechnology;
//import android.os.Parcelable;
import android.util.Log;

/**
 * @author uhahn
 *
 */
public class ReadNfcV extends Object implements TagTechnology {
	protected NfcV mynfcv;
	protected Tag mytag;
	
	private final String TAG=this.getClass().getName();
	
	protected boolean isTainted=true; // Tag info already read?
	protected byte[] mysysinfo=null;
	
	protected byte afi=0;
	public byte nBlocks=0;
	public byte blocksize=0;
	public byte[] Id;

	/**
	 * read new NfcV Tag from NFC device
	 */
	public ReadNfcV(Tag t) {
		mytag=t;
		Id = mytag.getId();
		mynfcv=NfcV.get(t);
		try {
			mynfcv.connect();
			mysysinfo=getSystemInformation(); 
			// fill in the fields..
			initfields();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			Log.d(TAG, "MyNfcV failed: "+e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * recreate NfcV Tag from log
	 * @param sysinfo: the logged system info only
	 */
	public ReadNfcV(String sysinfo){
		int startat=0;
		sysinfo.toLowerCase(); // ignore case
		if(sysinfo.startsWith("0x")){ // lets believe in HEX
			startat=2;
		}
		
		mysysinfo=hexStringToByteArray(sysinfo.substring(startat));
		initfields();
		isTainted=false; 
// TODO fake Tag?		mytag = Tag.CREATOR.createFromParcel(???);
	}

	/**
	 * recreate NfcV Tag from log
	 * @param sysinfo: the logged system info
	 * @param userdata: the logged userdata
	 */
	public ReadNfcV(String sysinfo, String userdata){
		int startat=0;
		sysinfo.toLowerCase(); // ignore case
		if(sysinfo.startsWith("0x")){ // lets believe in HEX
			startat=2;
		}
		
		mysysinfo=hexStringToByteArray(sysinfo.substring(startat));
		initfields();
		// TODO fake userdata
		isTainted=false;
		
	}
	
	/**
	 * parse system information byte array into attributes
	 * with respect to the flags found
	 * DSFID
	 * AFI
	 * memsize values (block count and length)
	 */
	private void initfields(){
		byte[] read=mysysinfo;
		if((null!=read)&&(12<read.length)&&(0==read[0])){// no error
			char flags=(char)read[1]; //s.charAt(1);

			//		String s=new String(read);
			//s.substring(2, 9).compareTo(Id.toString())  // the same?
			//set the Id from mysysinfo
			int pos=2;
			if(null==Id){ // dont overwrite, if given
				Id=new byte[8];
				for(int i=0;i<8;i++) 
					// @TODO find out, if Id to be reversed
					Id[i]=mysysinfo[pos + 7 - i];//reverse?!
			}
			pos=10; // start after flags, Infoflags and Id 
			if(0<(flags&0x1)){ // DSFID valid
				pos++; // already implemented 
			}
			if(0<(flags&0x2)){ // AFI valid
				afi=(byte)read[pos++];//s.charAt(pos++);
			}
			if(0<(flags&0x4)){ // memsize valid
				nBlocks=(byte)(read[pos++]+1);//(s.charAt(pos++)+1);
				blocksize=(byte)(read[pos++]+1); //((s.charAt(pos++)&0x1f)+1);
			}	
		}
	}
	
	/**
	 * @return the stored afi byte
	 */
	public byte getAFI(){
		if(isTainted){ // system info not read yet
			getSystemInformation(); // fill in the fields
		}
		return afi;
	}
	
	public byte getDsfId(){
		return mynfcv.getDsfId();
	}
	
	public int getblocksize(){
		return (int)blocksize;
	}
	
	public int getnBlocks(){
		return (int)nBlocks;
	}
	
	public byte[] getSystemInformation(){
			byte[] read=transceive((byte)0x2b);
			isTainted=false; // remember: we have read it and found it valid
			if(0==read[0]){// no error
				mysysinfo=read.clone();
				initfields(); // analyze 
				return mysysinfo;
			}
		return new byte[]{};
	}
	
	/**
	 * overload method transceive
	 * @return resulting array (or error?)
	 */
	protected byte[] transceive(byte cmd){
		return transceive(cmd, -1, -1, null);
	}

	protected byte[] transceive(byte cmd, int m){
		return transceive(cmd, m, -1, null);
	}
	
	protected byte[] transceive(byte cmd, int m ,int n){
		return transceive(cmd, m, n, null);
	}
	
	/**
	 * prepare and run the command according to NfcV specification
	 * @param cmd command byte
	 * @param m command length
	 * @param n 
	 * @param in input data
	 * @return
	 */
	protected byte[] transceive(byte cmd,int m, int n, byte[] in){
			byte[] command;
			byte[] res="transceive dummy message".getBytes();
		
		ByteArrayBuffer bab = new ByteArrayBuffer(128);
		// flags: bit x=adressed, 
		bab.append(0x00);
		bab.append(cmd); // cmd byte
		// 8 byte UID - or unaddressed
	//	bab.append(mytag.getId(), 0, 8);
		// block Nr
		if(-1!=m)bab.append(m);
		if(-1!=n)bab.append(n);
		if(null!=in)bab.append(in, 0, in.length);
		
		command=bab.toByteArray();
		Log.d(TAG,"transceive cmd: "+(command));
		Log.d(TAG,"transceive cmd length: "+command.length);
		
		try {
			res=mynfcv.transceive(command);
			if(0==mynfcv.getResponseFlags())
				return (res);
			else
	//			return new String("response Flags not 0").getBytes();
				return res;
		} 
		catch (TagLostException e){ //TODO roll back user action
			Log.e(TAG, "Tag lost "+e.getMessage());
			return e.getMessage().getBytes();		
		}
		catch (IOException e) {
			Log.d(TAG, "transceive failed: "+e.getMessage());
	//		e.printStackTrace();
			return e.getMessage().getBytes();
		}
	}

	
	/* (non-Javadoc)
	 * @see android.nfc.tech.TagTechnology#getTag()
	 */
	@Override
	public Tag getTag() {
		// TODO Auto-generated method stub
		return mytag;
	}
	
	/* (non-Javadoc)
	 * @see android.nfc.tech.TagTechnology#close()
	 */
	@Override
	public void close() throws IOException {
		try {
			mynfcv.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			Log.d(TAG, "close failed: "+e.getMessage());
			e.printStackTrace();
		}
	}

	/* (non-Javadoc)
	 * @see android.nfc.tech.TagTechnology#connect()
	 */
	@Override
	public void connect() throws IOException {
		try {
			mynfcv.connect();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			Log.d(TAG,"connect failed: "+e.getMessage());
			e.printStackTrace();
		}
	}

	/* (non-Javadoc)
	 * @see android.nfc.tech.TagTechnology#isConnected()
	 */
	@Override
	public boolean isConnected() {
		// TODO Auto-generated method stub
//		mynfcv.getDsfId();
		return mynfcv.isConnected(); // better?
	}

	public byte[] readSingleBlock(int i){
		return transceive((byte)0x20,i);
	}

	public byte[] readMultipleBlocks(int i,int j){
		if(0==blocksize) getSystemInformation(); // system info was not read yet

		byte[] read= transceive((byte)0x23,i,j);
		if(0!=read[0])return read; // error flag set: TODO  left as exercise..
		byte[] res=new byte[read.length-1]; // drop the (0) flag byte
		for (int l = 0; l < read.length-1; l++) {
			res[l]=read[l+1];
		}
		
		if(res.length<j*blocksize) return read; // da fehlt was
		for (int k = 0; k < j; k++) { // all blocks
// @TODO reverting block order should be done on demand - or under user control (done again in DDMData)
//		reverse(res,k*blocksize,blocksize); // swap string positions
		}
		return res;
	}

	/**
	 * move anywhere to utils
	 * @param s
	 * @return
	 */
	
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
