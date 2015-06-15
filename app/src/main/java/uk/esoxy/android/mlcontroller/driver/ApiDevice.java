package uk.esoxy.android.mlcontroller.driver;

import uk.esoxy.android.mlcontroller.ptp.PtpResult;
import uk.esoxy.android.mlcontroller.ptp.PtpTransaction;
import uk.esoxy.android.mlcontroller.utils.JavaBitmapCreator;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;

public class ApiDevice extends BaseDeviceDriver implements IDeviceDriver {

	UsbEndpoint inEp;
	UsbEndpoint outEp;
	UsbDeviceConnection conn;
	boolean useNeon;
	private JavaBitmapCreator bmpCreator;
	
	public ApiDevice(UsbDeviceConnection c, UsbEndpoint in, UsbEndpoint out, boolean useNeon) {
		inEp = in;
		outEp = out;
		conn = c;
		this.useNeon = useNeon;
		bmpCreator = new JavaBitmapCreator();
	}
	
	public int bulkTransferOut(byte[] data, int length, int timeout) {
		return conn.bulkTransfer(outEp, data, length, timeout);
	}

	public int bulkTransferIn(byte[] data, int length, int timeout) {
		return conn.bulkTransfer(inEp, data, length, timeout);
	}

	public PtpResult doTransaction(int opcode, int[] params, int direction, byte[] data) {
		return new PtpTransaction(this,opcode,params,direction,data).runTransaction();
	}

	byte[] dataGet;

	
	public Bitmap getLVImage(Bitmap bmp, boolean grayscale) {
		if (dataGet==null) {
			dataGet = new byte[256*1024];
		}
		PtpResult r;
		r = doTransaction(-28333, new int[] { 0x100000 }, PtpTransaction.DATA_RECV, dataGet);
		byte[] res = r.getData();
		int dataSize = r.getDataSize();
		if (res != null && dataSize > 20) {
			if (res.length > dataGet.length) {
				dataGet = res;
			}
			if (useNeon) {
				return NativeDevice.createBitmapFromData(bmp, res, grayscale);
			} else { 
				return bmpCreator.createBitmap(bmp, res, dataSize);
			}
		}
		return bmp;
	}

}
