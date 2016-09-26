package hu.telekom.lwi.plugin.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.StreamSinkConduit;

public class LwiResponseConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

		private LwiConduitWrapper parent;
	
		private StringBuffer responseBuffer;
		
		private long responseStarted = 0;
		
		private boolean dataAvailable = true;

		public LwiResponseConduit(StreamSinkConduit next, LwiConduitWrapper parent) {
			super(next);
			this.parent = parent;
			this.responseBuffer = new StringBuffer();
		}

		public long getResponseStarted() {
			return responseStarted;
		}
		
		public boolean isDataAvailable() {
			return dataAvailable;
		}

		public String getMessage() {
			synchronized (responseBuffer) {
				if (dataAvailable) {
					dataAvailable = false;
					// INFO: when parent callback happens the response buffer can be emptied - if parent is not calling back the response buffer will keep continue collecting data
					String request = responseBuffer.toString();
					responseBuffer.setLength(0);
					return request;
				}
			}
			return "";
		}
		
		@Override
		public int write(ByteBuffer src) throws IOException {
			if (responseStarted <= 0) {
				// INFO: response started with the first write
				responseStarted = System.currentTimeMillis();
			}

			int pos = src.position();
			int res = super.write(src);
			synchronized (responseBuffer) {
				if (res > 0) {
					byte[] d = new byte[res];
					for (int i = 0; i < res; ++i) {
						d[i] = src.get(i + pos);
					}
					responseBuffer.append(new String(d));
				}
	
				dataAvailable = true;
	
				if (responseBuffer.length() > LwiConduitWrapper.MAXBUFFER) {
					// INFO: notify parent that request buffer is full -> time to read
					parent.processResponse(false);
				}
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