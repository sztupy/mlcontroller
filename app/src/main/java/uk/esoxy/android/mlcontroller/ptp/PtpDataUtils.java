package uk.esoxy.android.mlcontroller.ptp;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class PtpDataUtils {
	static public String getString(ByteBuffer data) {
		int size = data.get();
		if (size == 0) {
			return "";
		} else {
			byte[] bb = new byte[size*2-2];
			data.get(bb);
			data.getShort();
			try {
				return new String(bb,"UTF-16LE");
			} catch (UnsupportedEncodingException e) {
				return "";
			}
		}
	}
	
	static public int[] getArray(ByteBuffer data, int dataSize) {
		int size = data.getInt();
		int[] result = new int[size];
		for (int i = 0; i< result.length; i++) {
			if (dataSize==2) result[i] = data.getShort();
			if (dataSize==4) result[i] = data.getInt();
		}
		return result;
	}
}
