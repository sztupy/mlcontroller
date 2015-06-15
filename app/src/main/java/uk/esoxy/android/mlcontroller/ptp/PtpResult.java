package uk.esoxy.android.mlcontroller.ptp;

public class PtpResult {
	private int mResult, mDataSize;
	private int[] mParams;
	private byte[] mData;
	
	PtpResult(int result, int[] params, byte[] data, int dataSize) {
		mResult = result;
		mParams = params;
		mData = data;
		mDataSize = dataSize;
	}
	
	PtpResult(int result, int[] params, byte[] data) {
		this(result,params,data,data==null?0:data.length);
	}
	
	PtpResult(int result, int[] params) {
		this(result, params, null);
	}
	
	PtpResult(int result) {
		this(result, new int[]{});
	}
	
	public byte[] getData() {
		return mData;
	}
	
	public int[] getParams() {
		return mParams;
	}
	
	public int getResult() {
		return mResult;
	}
	
	public int getDataSize() {
		return mDataSize;
	}
	
	@Override
	public String toString() {
		StringBuffer s = new StringBuffer();
		s.append(mResult);s.append(" ");
		for (int i:mParams) { s.append(i);s.append(" "); }
		if (mData != null) s.append(String.format("Length:  %d",mData.length));
		return s.toString();
	}
}
