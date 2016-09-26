package hu.telekom.lwi.plugin.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.xnio.IoUtils;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.ConduitReadableByteChannel;
import org.xnio.conduits.StreamSourceConduit;

public class LwiRequestConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {

	private LwiConduitWrapper parent;

	private StringBuffer requestBuffer;
	
	private long requestFinished = 0;
	
	private boolean dataAvailable = true;
	
	public LwiRequestConduit(StreamSourceConduit next, LwiConduitWrapper parent) {
		super(next);
		this.parent = parent;
		this.requestBuffer = new StringBuffer();
	}

	public long getRequestFinished() {
		return requestFinished;
	}

	public boolean isDataAvailable() {
		return dataAvailable;
	}

	public String getMessage() {
		synchronized (requestBuffer) {
			if (dataAvailable) {
				dataAvailable = false;
				// INFO: when parent callback happens the request buffer can be emptied - if parent is not calling back the request buffer will keep continue collecting data
				String request = requestBuffer.toString();
				requestBuffer.setLength(0);
				return request;
			}
		}
		return "";
	}
	
	@Override
	public int read(ByteBuffer dst) throws IOException {
		int pos = dst.position();
		int res = super.read(dst);

		synchronized (requestBuffer) {
			if (res > 0) {
				byte[] d = new byte[res];
				for (int i = 0; i < res; ++i) {
					d[i] = dst.get(i + pos);
				}
				requestBuffer.append(new String(d));
				requestFinished = System.currentTimeMillis();
			}
	
			dataAvailable = true;
			
			if (requestBuffer.length() > LwiConduitWrapper.MAXBUFFER) {
				// INFO: notify parent that request buffer is full -> time to read
				parent.processRequest(false);
			}
		}
		return res;
	}

	@Override
	public long transferTo(long position, long count, FileChannel target) throws IOException {
		return target.transferFrom(new ConduitReadableByteChannel(this), position, count);
	}

	@Override
	public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
		return IoUtils.transfer(new ConduitReadableByteChannel(this), count, throughBuffer, target);
	}

	@Override
	public long read(ByteBuffer[] dsts, int offs, int len) throws IOException {
		for (int i = offs; i < len; ++i) {
			if (dsts[i].hasRemaining()) {
				return read(dsts[i]);
			}
		}
		return 0;
	}

}