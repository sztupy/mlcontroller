package uk.esoxy.android.mlcontroller.driver;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import android.graphics.Bitmap;
import android.util.Log;

import uk.esoxy.android.mlcontroller.commands.ChangeUSBPermissionsCommand;
import uk.esoxy.android.mlcontroller.ptp.PtpResult;
import uk.esoxy.android.mlcontroller.ptp.PtpTransaction;
import uk.esoxy.android.mlcontroller.utils.FileUtils;
import uk.esoxy.android.mlcontroller.utils.JavaBitmapCreator;

public class NativeDevice extends BaseDeviceDriver implements IDeviceDriver, Serializable {
	private static final long serialVersionUID = 8067997436114407710L;
	private String position;
	private String dev;
	private int idProduct;
	private int idVendor;
	private String product;
	private String manufacturer;
	private String configuration;
	private String serial;
		
	private boolean isOpen;
	private boolean isClaimed;
	private static boolean nativeOkay;
	private static int hasNeon;
	boolean useNeon;
	
	// set by native methods
	private int nativeStruct;
	private int inEndpoint;
	private int outEndpoint;
	private JavaBitmapCreator bmpCreator;
	
	native boolean nativeOpen(boolean reset);
	native void nativeClose();
	native int nativeClaimInterface(int intface);
	native int nativeReleaseInterface(int intface);
	native boolean nativeCheckPtp();
	native int nativeBulkTransfer(int endpoint, byte[] buffer,int length, int timeout);
	native PtpResult nativePtpTransaction(int mOpcode, int[] mParams, int mDirection, byte[] mData);
	native Bitmap nativeGetLvImage(Bitmap bmp, boolean grayscale);
	static native Bitmap nativeCreateBitmapFromData(Bitmap bmp, byte[] data, boolean grayscale);
	static native boolean nativeGetNeon();
	
	static {
        try {
    		System.loadLibrary("ptp");
    		nativeOkay = true;
        }
        catch (UnsatisfiedLinkError ule) {
            Log.e("JNI", "ERROR: Could not load libptp.so");
            nativeOkay = false;
        }
	}

	public NativeDevice(File element, boolean useNeon) throws IOException {
		File cn = element.getCanonicalFile();
		position = element.getName();
		dev = FileUtils.ReadFile(cn, "dev");
		idProduct = Integer.parseInt(FileUtils.ReadFile(cn, "idProduct"),16);
		idVendor = Integer.parseInt(FileUtils.ReadFile(cn, "idVendor"),16);
		product = FileUtils.ReadFile(cn, "product");
		serial = FileUtils.ReadFile(cn, "serial");
		manufacturer = FileUtils.ReadFile(cn, "manufacturer");
		configuration = FileUtils.ReadFile(cn, "configuration");
		isOpen = false;
		isClaimed = false;
		bmpCreator = new JavaBitmapCreator();
		this.useNeon = useNeon;
	}

	public String getPosition() {
		return position;
	}

	public String getDev() {
		return dev;
	}

	public int getIdProduct() {
		return idProduct;
	}

	public int getIdVendor() {
		return idVendor;
	}

	public String getProduct() {
		return product;
	}

	public String getManufacturer() {
		return manufacturer;
	}

	public String getConfiguration() {
		return configuration;
	}

	public String getSerial() {
		return serial;
	}

	public static boolean isNativeOkay() {
		return nativeOkay;
	}
	
	public boolean open(boolean force) {
		if (!nativeOkay) return false;
		if (isOpen) return true;
		nativeStruct = 0;
		boolean success = nativeOpen(force);
		if (!success) {
			ChangeUSBPermissionsCommand c = new ChangeUSBPermissionsCommand();
			c.execute();
			success = nativeOpen(force);
		}
		isOpen = success;
		return success;
	}
		
	public void close() {
		if (!nativeOkay) return;
		if (!isOpen) return;
		if (isClaimed) releaseInterface();
		nativeClose();
		nativeStruct = 0;
		isOpen = false;
	}
	
	public boolean claimInterface() {
		if (!isClaimed) {
			int ret = nativeClaimInterface(0);
			if (ret==0) isClaimed = true;
			return ret==0;
		} else {
			return true;
		}
	}
	
	public boolean releaseInterface() {
		if (isClaimed) {
			int ret = nativeReleaseInterface(0);
			if (ret==0) isClaimed = false;
			return ret==0;
		} else return true;
	}
	
	public boolean checkPtp() {
		return nativeCheckPtp();
	}
	
	@Override
	protected void finalize() throws Throwable {
		close();
		super.finalize();
	}
	
	public int bulkTransferOut(byte[] data, int length, int timeout) {
		return nativeBulkTransfer(outEndpoint, data, length, timeout);
	}
	public int bulkTransferIn(byte[] data, int length, int timeout) {
		return nativeBulkTransfer(inEndpoint, data, length, timeout);
	}
	public PtpResult doTransaction(int opcode, int[] params, int direction, byte[] data) {
		synchronized(this) {
			return nativePtpTransaction(opcode, params, direction,data);
		}
	}
	
	byte[] dataGet;
	
	public Bitmap getLVImage(Bitmap bmp, boolean grayscale) {
		if (getNeon() && useNeon) {
			return nativeGetLvImage(bmp, grayscale);
		} else {
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
				return bmpCreator.createBitmap(bmp, res, dataSize);
			}
			return bmp;
		}
	}
	
	static public Bitmap createBitmapFromData(Bitmap bmp, byte[] data, boolean grayscale) {
		return nativeCreateBitmapFromData(bmp, data, grayscale);
	}
	
	static public boolean getNeon() {
		if (hasNeon!=1 && hasNeon!=2) {
			boolean hN = nativeGetNeon();
			if (hN) hasNeon = 1; else hasNeon = 2; 
		}
		return hasNeon == 1;
	}
}
