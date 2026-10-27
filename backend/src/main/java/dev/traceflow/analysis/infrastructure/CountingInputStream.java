package dev.traceflow.analysis.infrastructure;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Counts the bytes read from the underlying stream, used to report ingestion progress. */
public final class CountingInputStream extends FilterInputStream {

    private volatile long count;

    public CountingInputStream(InputStream in) {
        super(in);
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b >= 0) {
            count++;
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n > 0) {
            count += n;
        }
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = super.skip(n);
        count += skipped;
        return skipped;
    }

    public long count() {
        return count;
    }
}
