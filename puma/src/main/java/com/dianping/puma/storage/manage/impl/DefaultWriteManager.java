package com.dianping.puma.storage.manage.impl;

import com.dianping.puma.common.AbstractLifeCycle;
import com.dianping.puma.core.codec.EventCodec;
import com.dianping.puma.core.event.ChangedEvent;
import com.dianping.puma.core.event.RowChangedEvent;
import com.dianping.puma.core.model.BinlogInfo;
import com.dianping.puma.storage.Sequence;
import com.dianping.puma.storage.data.DataBucketManager;
import com.dianping.puma.storage.data.WriteDataBucket;
import com.dianping.puma.storage.index.IndexKeyImpl;
import com.dianping.puma.storage.index.IndexValueImpl;
import com.dianping.puma.storage.index.WriteIndexManager;
import com.dianping.puma.storage.manage.WriteManager;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DefaultWriteManager extends AbstractLifeCycle implements WriteManager {

	private EventCodec codec;

	private WriteDataBucket writeDataBucket;

	private DataBucketManager masterDataBucketManager;

	private WriteIndexManager<IndexKeyImpl, IndexValueImpl> writeIndexManager;

	private Sequence writeSequence;

	private Sequence flushSequence;

	private long currServerId;

	private String currDate;

	private SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");

	@Override
	protected void doStart() {

	}

	@Override
	protected void doStop() {

	}

	@Override
	public Sequence writeSequence() {
		return writeSequence;
	}

	@Override
	public Sequence flushSequence() {
		return flushSequence;
	}

	@Override
	public void append(BinlogInfo binlogInfo, ChangedEvent binlogEvent) throws IOException {
		binlogEvent.setSeq(writeSequence.longValue());

		if (needToNextWriteDataBucket(binlogInfo)) {
			writeDataBucket = genNextWriteDataBucket();

			// Writes to L1 index bucket if a new data bucket generated.
			writeL1Index(binlogEvent);
		}

		// Writes to L2 index bucket and data bucket.
		writeL2Index(binlogEvent);
		int length = writeData(binlogEvent);

		writeSequence.incrOffset(length);
		currDate = sdf.format(new Date());
		currServerId = binlogInfo.getServerId();
	}

	protected boolean needToNextWriteDataBucket(BinlogInfo binlogInfo) {
		// Current write data bucket is full.
		if (!writeDataBucket.hasRemainingForWrite()) {
			return true;
		}

		// Current date changed(e.g. 20151010 -> 20151011).
		if (!currDate.equals(sdf.format(new Date()))) {
			return true;
		}

		// Current source mysql server id changed.
		if (currServerId != binlogInfo.getServerId()) {
			return true;
		}

		return false;
	}

	protected WriteDataBucket genNextWriteDataBucket() throws IOException {
		return masterDataBucketManager.genNextWriteDataBucket();
	}

	protected void writeL1Index(ChangedEvent binlogEvent) throws IOException {
		IndexKeyImpl indexKey = new IndexKeyImpl(
				binlogEvent.getExecuteTime(),
				binlogEvent.getBinlogInfo().getServerId(),
				binlogEvent.getBinlogInfo().getBinlogFile(),
				binlogEvent.getBinlogInfo().getBinlogPosition()
		);

		writeIndexManager.addL1Index(indexKey, writeDataBucket.name());
	}

	protected void writeL2Index(ChangedEvent binlogEvent) throws IOException {
		IndexKeyImpl indexKey = new IndexKeyImpl(
				binlogEvent.getExecuteTime(),
				binlogEvent.getBinlogInfo().getServerId(),
				binlogEvent.getBinlogInfo().getBinlogFile(),
				binlogEvent.getBinlogInfo().getBinlogPosition()
		);

		IndexValueImpl l2Index = new IndexValueImpl();
		if (binlogEvent instanceof RowChangedEvent) {
			l2Index.setDml(true);
			l2Index.setTransactionBegin(((RowChangedEvent) binlogEvent).isTransactionBegin());
			l2Index.setTransactionCommit(((RowChangedEvent) binlogEvent).isTransactionCommit());
		} else {
			l2Index.setDdl(true);
		}
		l2Index.setSequence(new Sequence(binlogEvent.getSeq(), 0));
		l2Index.setIndexKey(indexKey);

		writeIndexManager.addL2Index(indexKey, l2Index);
	}

	protected int writeData(ChangedEvent binlogEvent) throws IOException {
		byte[] data = codec.encode(binlogEvent);
		writeDataBucket.append(data);
		return data.length;
	}

	private class FlushTask implements Runnable {
		@Override
		public void run() {
			flush();
		}
	}

	@Override
	public void flush() {

	}
}