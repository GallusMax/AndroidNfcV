/*
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
import java.nio.ByteBuffer;
import java.util.HashMap;

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
	
	// iso15693 flags
	public static final byte FLAG_OPTION=0x40;
	public static final byte FLAG_ADDRESSED=0x20;
	
	protected NfcV mynfcv;
//	protected Tag mytag; // can be retrieved through mynfcv
	
	private final String TAG=this.getClass().getName();
	protected final int maxretry=3;
	public int lastretried=0;
	
	protected boolean isTainted=true; // Tag info already read?
	protected byte[] mysysinfo=null;	// NfcV SystemInformation - or generated
	protected byte[] myuserdata=null;	// buffer user content
	protected boolean[] blocktainted;	// true when block is to be uploaded to tag
	protected byte[] blocklocked;		// 0 means writable
	
	protected byte afi=0;
	public byte nBlocks=0;
	public byte blocksize=0;
	public byte[] Id;
	public byte[] UID; // becomes valid when a real tag is contacted
	public byte DSFID = -1;
	public int maxtrans=0; // tag dependent max transceive length
	public byte lastErrorFlags=-1; // re-set by each transceive
	public byte lastErrorCode=-1; // re-set by each transceive
	public byte manuByte=0;
	
	public static final byte BYTE_IDSTART=(byte)0xe0;
	public static final byte MANU_TAGSYS=0x04;
	public static final HashMap<Byte,String> manuMap = new HashMap<Byte, String>();
	
	static{
		manuMap.put(MANU_TAGSYS, "TagSys");
	}
	
	/**
	 * read new NfcV Tag from NFC device
	 */
	public ReadNfcV(Tag t) {
		UID = t.getId(); // sysinfo holds the UID in lsb order -  var Id will be filled lateron from sysinfo!
		Log.d(TAG,"getId: "+toHex(t.getId()));
		mynfcv=NfcV.get(t);
		try {
			mynfcv.connect(); //TODO in background thread
			Log.d(TAG,"getResponseFlags after connect: "+mynfcv.getResponseFlags()); 

			mysysinfo=getSystemInformation(); 
			// explore Nfcv properties..
			//initfields(); // done by getSys..

			maxtrans=mynfcv.getMaxTransceiveLength();
			DSFID=mynfcv.getDsfId();
			Log.d(TAG,nBlocks + " x " + blocksize + " bytes");
			blocklocked=new byte[nBlocks]; // init the lock shadow
//			getMultiSecStatus(0, nBlocks);	// and fill from tag TODO find out what crashes on Philips
			
			blocktainted=new boolean[nBlocks];
			taintblock(0,nBlocks);
			
//			Log.d(TAG,"maxtrans "+maxtrans);
			// init space for userdata ?
			myuserdata= new byte[nBlocks*blocksize]; 
		} catch (IOException e) {
			lastErrorFlags=-1;
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
		// init space for userdata TODO limit size?
		//myuserdata= new byte[nBlocks*blocksize]; 
		isTainted=false; 
// TODO fake Tag?		mytag = Tag.CREATOR.createFromParcel(???);
	}

	/**
	 * recreate NfcV Tag from log
	 * @param sysinfo: the logged system info
	 * @param userdata: the logged userdata
	 */
	public ReadNfcV(String sysinfo, String userdata){
		this(sysinfo);
		// TODO fake userdata
		int startat=0;
		userdata.toLowerCase(); // ignore case
		if(userdata.startsWith("0x")){ // lets believe in HEX
			startat=2;
		}
		myuserdata=hexStringToByteArray(userdata.substring(startat));
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

			//	String s=new String(read);
			//s.substring(2, 9).compareTo(Id.toString())  // the same?
			//set the Id from mysysinfo
			int pos=2;
			boolean forwardId=false; // the Id field is in lsb order
			if(BYTE_IDSTART==read[pos]){
				forwardId=true;
				manuByte=read[pos+1];
			}else if(BYTE_IDSTART==read[pos+7]){
				manuByte=read[pos+6];
				forwardId=false;
			}else
				Log.e(TAG,"Id start byte not found where expected");
			if(null==Id){ // dont overwrite, if given
				Id=new byte[8];
				for(int i=0;i<8;i++) 
					// TODO decide if Id to be reversed (Zebra needs msb order, that is Id[7] changes between tags)
					Id[i]=(forwardId? read[pos+i] : read[pos + 7 - i]); //reverse?!
				Log.d(TAG,"Id from sysinfo (reversed): "+toHex(Id));
			}
			
			pos=10; // start after flags, Infoflags and Id TODO: change if transceive should eat up the error byte 
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
//		return mynfcv.getDsfId(); // avoid re-reading
		return DSFID;
	}
	
	public int getblocksize(){
		return (int)blocksize;
	}
	
	public int getnBlocks(){
		return (int)nBlocks;
	}
	
	public byte[] getSystemInformation(){
		if(isTainted){ // dont reread 
			mysysinfo=transceive((byte)0x2b);
			isTainted=false; // remember: we have read it and found it valid
			if(0==lastErrorFlags){// no error
				isTainted=false; // remember: we have read it and found it valid
				initfields(); // analyze 
			}}
		return mysysinfo;
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
	protected synchronized byte[] transceive(byte cmd,int m, int n, byte[] in){
			byte[] command;
			byte[] res="xxxtransceive failed message".getBytes();
			res[0]=0x42; // pos 0 holds the error flag
			
		ByteArrayBuffer bab = new ByteArrayBuffer(128);
//		bab.append(FLAG_ADDRESSED); // try addressed operations on protected tags
		bab.append(0x00); // try addressed operations on protected tags
		bab.append(cmd); // cmd byte
		// 8 byte UID - or unaddressed
//		bab.append(mynfcv.getTag().getId(), 0, 8); // what if tag is gone?
//		bab.append(UID, 0, 8); 
		// block Nr
		if(-1!=m)bab.append(m);
		if(-1!=n)bab.append(n);
		if(null!=in)bab.append(in, 0, in.length);
		
		command=bab.toByteArray();
//		Log.d(TAG,"transceive cmd: "+toHex(command));
//		Log.d(TAG,"transceive cmd length: "+command.length);
		
		for(int t=0;t<=maxretry;t++){ // retry reading
		// TODO background!
		try {
			if(!mynfcv.isConnected()) return " connect lost".getBytes();
				res=mynfcv.transceive(command);
			}
		
		catch (TagLostException e){ //TODO roll back user action
//			Log.e(TAG, String.format("transceive: %x",cmd) +e.getMessage());
			Log.e(TAG,"transceive cmd: "+toHex(command)+e.getMessage());
			try {
				mynfcv.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			return e.getMessage().getBytes();		
		}
		catch (IOException e) {
			Log.e(TAG, "transceive IOEx: "+toHex(command)+e.getMessage()+toHex(res));
//			Log.d(TAG,"command len: "+command.length);
//			Log.d(TAG,"maxtranslen: "+maxtrans);
	//		e.printStackTrace();
			return e.getMessage().getBytes();
		}
		finally{
			lastErrorFlags=res[0];
			if(0!=lastErrorFlags){
				lastErrorCode=res[1];
				Log.d(TAG,"ErrorCodebyte: "+String.format("%2x", lastErrorCode));
			}
		}

		lastretried=t; // keep last count
		if(0==res[0]) break;
		Log.d(TAG,"transceive retry "+t);
		}
//		Log.d(TAG,"transceive done after "+lastretried+" retries");

		return res;
	}

	
	public void taintblock(int i, int n){
		for(int j=0;j<n;j++)
			setblocktaint(j,true);
	}

	public void taintblock(int i){
		setblocktaint(i,true);
	}
	
	protected void setblocktaint(int i, boolean b){
		blocktainted[i]=b;
	}
	
	
	/* (non-Javadoc)
	 * @see android.nfc.tech.TagTechnology#getTag()
	 * 
	 */
	@Override
	public Tag getTag() {
		// TODO Auto-generated method stub
		//return mytag;
		return mynfcv.getTag();
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
			lastErrorFlags=-1; // TODO discriminate error states
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
		byte[] read=transceive((byte)0x20,i);
		
		setblocktaint(i,false); // remember we read this block
		if(0!=lastErrorFlags)return read; // TODO not so ignorant..
		
		byte[] res=new byte[read.length-1]; // drop the (0) flag byte TODO: in transceive?
		for (int l = 0; l < read.length-1; l++) {
			res[l]=read[l+1];
			myuserdata[i*blocksize+l]=res[l]; // sort block into our buffer
		}
		
		return res;
		
	}

	public byte[] readMultipleBlocks(int i,int j){
		ByteArrayBuffer bab = new ByteArrayBuffer(32);
		
		for(int bn=i;bn<i+j;bn++){
			byte[] singleBlock=readSingleBlock(bn);
			bab.append(singleBlock,0,singleBlock.length);
		}
		return bab.buffer();
	}
	
	/**
	 * 
	 * @param i starting block number
	 * @param j block count
	 * @return block content concatenated 
	 */
	public byte[] ISOreadMultipleBlocks(int i,int j){
		if(0==blocksize){
			Log.e(TAG,"readMult w/o initfields?");
			getSystemInformation(); // system info was not read yet
		}

		byte[] read = transceive((byte)0x23,i,j);
		if(0!=read[0])return read; // error flag set: TODO  left as exercise..

		byte[] res=new byte[read.length-1]; // drop the (0) flag byte
		for (int l = 0; l < read.length-1; l++) {
			res[l]=read[l+1];
			myuserdata[i*blocksize+l]=res[l]; // sort block into our buffer
		}
		
		if(res.length<j*blocksize) return read; // da fehlt was
		for (int k = i; k < j; k++) { // all blocks we read
			setblocktaint(k, false); // untaint blocks we read
// @TODO reverting block order should be done on demand - or under user control (done again in DDMData)
//		reverse(res,k*blocksize,blocksize); // swap string positions
		}
		return res;
	}
	
	public byte[] getMultiSecStatus(int i,int n){
		byte[] read = transceive((byte)0x2c,i,n-1);
		Log.d(TAG,"secstatus "+toHex(read));
		if(0!=read[0])return read;
		int startat=1; // TODO transceive will skip the error field soon
		
		for(int j=0;j<nBlocks;j++)
			blocklocked[j]=read[startat+i+j];

		return read;
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
