package uk.esoxy.android.mlcontroller.utils;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory.Options;

public class JavaBitmapCreator implements Serializable {
	private static final long serialVersionUID = 7266820573579070195L;
	
	byte[] tmpStor;
	byte[] bmpBuf;
	
	public JavaBitmapCreator() {
		if (tmpStor==null) {
			tmpStor = new byte[32766];
		}
	}
	
	public Bitmap createBitmap(Bitmap bmp, byte[] res, int dataSize) {
		BitmapFactory.Options o = new Options();
		ByteBuffer buf = ByteBuffer.wrap(res);
		buf.order(ByteOrder.LITTLE_ENDIAN);
		if (buf.get(8)!=-1 || buf.get(9)!=-40 ) {
			int width = buf.getInt(12);
			int height = buf.getInt(16);
			if (width*height > 0 && width*height*2 <= dataSize) {
				if (bmp==null || bmp.getWidth() != width || bmp.getHeight() != height) {
					bmp = Bitmap.createBitmap(width, height, Config.ARGB_8888);
				}
				if (bmpBuf==null || bmpBuf.length<width*height*4) {
					bmpBuf = new byte[width*height*4];
				}
				ByteBuffer bmpB = ByteBuffer.wrap(bmpBuf);
				buf.position(0x28);
				for (int i=0; i<width*height / 2; i++) {
					buf.get();
					byte y1 = buf.get();
					buf.get();
					byte y2 = buf.get();
					bmpB.put(y1);
					bmpB.put(y1);
					bmpB.put(y1);
					bmpB.put((byte)-1);
					bmpB.put(y2);
					bmpB.put(y2);
					bmpB.put(y2);
					bmpB.put((byte)-1);						
				}
				bmpB.position(0);
				bmp.copyPixelsFromBuffer(bmpB);
			}
		} else {
			if (bmp!=null) {
				o.inBitmap = bmp;
			}
			o.inSampleSize = 1;
			o.inTempStorage = tmpStor;
			try {
				Bitmap b = BitmapFactory.decodeByteArray(res, 8, dataSize-8, o);
				if (b!=null) {
					return b;
				}
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
		return bmp;
	}
}
