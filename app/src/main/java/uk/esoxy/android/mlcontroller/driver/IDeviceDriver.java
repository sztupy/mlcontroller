package uk.esoxy.android.mlcontroller.driver;

import android.graphics.Bitmap;
import uk.esoxy.android.mlcontroller.ptp.PtpDeviceDescriptor;
import uk.esoxy.android.mlcontroller.ptp.PtpResult;
import uk.esoxy.android.mlcontroller.ptp.ml.MlDeviceDescriptor;

public interface IDeviceDriver {
	int bulkTransferOut(byte[] data, int length, int timeout);
	int bulkTransferIn(byte[] data, int length, int timeout);
	PtpResult doTransaction(int opcode, int[] params, int direction, byte[] data);
	Bitmap getLVImage(Bitmap b, boolean grayscale); 
	
	public PtpDeviceDescriptor getDeviceDescriptor() throws InvalidResult;
	public boolean startSession() throws InvalidResult;
	public MlDeviceDescriptor getMlDescriptor() throws InvalidResult;
	
	public class InvalidResult extends Exception {
		private static final long serialVersionUID = 4437939371616407501L;
		
		int resultCode;
		
		public InvalidResult(int resultCode) {
			this.resultCode = resultCode;
		}
				
		public int getResultCode() {
			return resultCode;
		}
	}
}
