package uk.esoxy.android.mlcontroller.driver;

import uk.esoxy.android.mlcontroller.ptp.PtpDeviceDescriptor;
import uk.esoxy.android.mlcontroller.ptp.PtpResult;
import uk.esoxy.android.mlcontroller.ptp.PtpTransaction;
import uk.esoxy.android.mlcontroller.ptp.ml.MlDeviceDescriptor;

public abstract class BaseDeviceDriver implements IDeviceDriver {
	private PtpDeviceDescriptor deviceDescriptor;
	private MlDeviceDescriptor mlDeviceDescriptor;
	
	public PtpDeviceDescriptor getDeviceDescriptor() throws InvalidResult {
		if (deviceDescriptor == null) {
			PtpResult r = doTransaction(0x1001, new int[] {}, PtpTransaction.DATA_RECV, null);
			if (r.getResult()!=0x2001) {
				throw new InvalidResult(r.getResult());
			}
			deviceDescriptor = new PtpDeviceDescriptor(r.getData());
		}
		return deviceDescriptor;
	} 
	
	public MlDeviceDescriptor getMlDescriptor() throws InvalidResult {
		if (mlDeviceDescriptor == null) {
			mlDeviceDescriptor = new MlDeviceDescriptor(this);
		}
		return mlDeviceDescriptor;
	} 
	
	public boolean startSession() throws InvalidResult {
		PtpResult r = doTransaction(0x1002, new int[] {1},PtpTransaction.DATA_NONE, null);
		if (r.getResult()!=0x2001) {
			throw new InvalidResult(r.getResult());
		}
		return true;
	}
	
}
