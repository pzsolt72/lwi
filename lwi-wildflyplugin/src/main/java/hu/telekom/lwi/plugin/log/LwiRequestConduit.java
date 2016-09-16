package hu.telekom.lwi.plugin.log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.jboss.logging.Logger;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.ConduitReadableByteChannel;
import org.xnio.conduits.StreamSourceConduit;

import hu.telekom.lwi.plugin.util.LwiLogAttributeUtil;

public class LwiRequestConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {

	private LwiConduitWrapper parent;

	private StringBuffer requestBuffer;
	private int partCounter = 0;
	
	private long requestFinished = 0;
	
	private boolean logAvailable = false;
	
	protected LwiRequestConduit(StreamSourceConduit next, LwiConduitWrapper parent) {
		super(next);
		this.parent = parent;
		this.requestBuffer = new StringBuffer();
	}

	public long getRequestFinished() {
		return requestFinished;
	}
	
	public void log(Logger messageLog, String logPrefix, boolean partial) {
		if (logAvailable) {
			if (partCounter++ > 0 || partial) {
				messageLog.info(String.format("%s[REQUEST (partial request part - %s) > %s]", logPrefix, (partial ? partCounter : "last"), LwiLogAttributeUtil.cleanseMessage(requestBuffer.toString())));
			} else {
				messageLog.info(String.format("%s[REQUEST > %s]", logPrefix, LwiLogAttributeUtil.cleanseMessage(requestBuffer.toString())));
			}
			requestBuffer.setLength(0);
			logAvailable = false;
		}
	}

	@Override
	public int read(ByteBuffer dst) throws IOException {

		int pos = dst.position();
		int res = super.read(dst);

		if (res > 0) {
			byte[] d = new byte[res];
			for (int i = 0; i < res; ++i) {
				d[i] = dst.get(i + pos);
			}
			requestBuffer.append(new String(d));
			requestFinished = System.currentTimeMillis();
		}

		logAvailable = true;
		
		if (parent.getLwiRequestData().parseRequestRequired()) {
			String request = requestBuffer.toString();
			if (parent.getLwiRequestData().isNullRequestId()) {
				parent.getLwiRequestData().setRequestId(LwiLogAttributeUtil.getMessageAttribute(LwiLogAttribute.RequestId, request));
			}
			if (parent.getLwiRequestData().isNullCorrelationId()) {
				parent.getLwiRequestData().setCorrelationId(LwiLogAttributeUtil.getMessageAttribute(LwiLogAttribute.CorrelationId, request));
			}
			if (parent.getLwiRequestData().isNullUserId()) {
				parent.getLwiRequestData().setUserId(LwiLogAttributeUtil.getMessageAttribute(LwiLogAttribute.UserId, request));
			}
		}

		if (requestBuffer.length() > LwiConduitWrapper.MAXBUFFER) {
			parent.logRequest(true);
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