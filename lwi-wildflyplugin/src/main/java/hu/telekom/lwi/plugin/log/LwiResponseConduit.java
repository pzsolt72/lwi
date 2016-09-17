package hu.telekom.lwi.plugin.log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.jboss.logging.Logger;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.StreamSinkConduit;

import hu.telekom.lwi.plugin.util.LwiLogAttributeUtil;

public class LwiResponseConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

		private LwiConduitWrapper parent;
	
		private StringBuffer responseBuffer;
		private int partCounter = 0;
		
		private long responseStarted = 0;
		
		private boolean logAvailable = false;

		protected LwiResponseConduit(StreamSinkConduit next, LwiConduitWrapper parent) {
			super(next);
			this.parent = parent;
			this.responseBuffer = new StringBuffer();
		}

		public long getResponseStarted() {
			return responseStarted;
		}
		
		public void log(Logger messageLog, String logPrefix, boolean partial) {
			if (logAvailable) {
				if (partCounter++ > 0 || partial) {
					messageLog.info(String.format("%s[RESPONSE (partial response part - %s) > %s]", logPrefix, (partial ? partCounter : "last"), LwiLogAttributeUtil.cleanseMessage(responseBuffer.toString())));
				} else {
					messageLog.info(String.format("%s[RESPONSE > %s]", logPrefix, LwiLogAttributeUtil.cleanseMessage(responseBuffer.toString())));
				}
				logAvailable = false;
				responseBuffer.setLength(0);
			}
		}

		@Override
		public int write(ByteBuffer src) throws IOException {

			if (responseStarted <= 0) {
				responseStarted = System.currentTimeMillis();
			}

			int pos = src.position();
			int res = super.write(src);
			if (res > 0) {
				byte[] d = new byte[res];
				for (int i = 0; i < res; ++i) {
					d[i] = src.get(i + pos);
				}
				responseBuffer.append(new String(d));
			}

			logAvailable = true;

			if (responseBuffer.length() > LwiConduitWrapper.MAXBUFFER) {
				parent.logResponse(true);
			}

			return res;
		}

		@Override
		public long write(ByteBuffer[] dsts, int offs, int len) throws IOException {
			for (int i = offs; i < len; ++i) {
				if (dsts[i].hasRemaining()) {
					return write(dsts[i]);
				}
			}
			return 0;
		}

		@Override
		public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
			return src.transferTo(position, count, new ConduitWritableByteChannel(this));
		}

		@Override
		public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
			return IoUtils.transfer(source, count, throughBuffer, new ConduitWritableByteChannel(this));
		}

		@Override
		public int writeFinal(ByteBuffer src) throws IOException {
			return Conduits.writeFinalBasic(this, src);
		}

		@Override
		public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
			return Conduits.writeFinalBasic(this, srcs, offset, length);
		}

	}