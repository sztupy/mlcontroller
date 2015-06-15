package uk.esoxy.android.mlcontroller.ptp.ml;

import uk.esoxy.android.mlcontroller.driver.IDeviceDriver;
import uk.esoxy.android.mlcontroller.driver.IDeviceDriver.InvalidResult;
import uk.esoxy.android.mlcontroller.ptp.PtpResult;
import uk.esoxy.android.mlcontroller.ptp.PtpTransaction;

public class MlDeviceDescriptor {
	int usbMinor;
	int usbMajor;
	
	String buildVersion;
	String buildId;
	String buildDate;
	String buildUser;
	
	public int getUsbMinor() {
		return usbMinor;
	}

	public int getUsbMajor() {
		return usbMajor;
	}

	public String getBuildVersion() {
		return buildVersion;
	}

	public String getBuildId() {
		return buildId;
	}

	public String getBuildDate() {
		return buildDate;
	}

	public String getBuildUser() {
		return buildUser;
	}

	public MlDeviceDescriptor(IDeviceDriver dev) throws InvalidResult {
		PtpResult r = dev.doTransaction(-24088, new int[] { 0 }, PtpTransaction.DATA_NONE, null);
		if (r.getResult()!=0x2001) {
			throw new InvalidResult(r.getResult());
		}
		usbMajor = r.getParams()[0];
		usbMinor = r.getParams()[1];
		
		r = dev.doTransaction(-24088, new int[] { 1, 0 }, PtpTransaction.DATA_RECV, null);
		if (r.getResult()!=0x2001) {
			throw new InvalidResult(r.getResult());
		}
		buildVersion = new String(r.getData());
		r = dev.doTransaction(-24088, new int[] { 1, 1 }, PtpTransaction.DATA_RECV, null);
		if (r.getResult()!=0x2001) {
			throw new InvalidResult(r.getResult());
		}
		buildId = new String(r.getData());
		r = dev.doTransaction(-24088, new int[] { 1, 2 }, PtpTransaction.DATA_RECV, null);
		if (r.getResult()!=0x2001) {
			throw new InvalidResult(r.getResult());
		}
		buildDate = new String(r.getData());
		r = dev.doTransaction(-24088, new int[] { 1, 3 }, PtpTransaction.DATA_RECV, null);
		if (r.getResult()!=0x2001) {
			throw new InvalidResult(r.getResult());
		}
		buildUser = new String(r.getData());
	}
	
	
}
